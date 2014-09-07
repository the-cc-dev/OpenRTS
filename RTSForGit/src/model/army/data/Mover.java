/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package model.army.data;

import model.army.motion.pathfinding.Path;
import model.army.motion.pathfinding.FlowField;
import geometry.AlignedBoundingBox;
import geometry.BoundingCircle;
import geometry.Point2D;
import geometry3D.Point3D;
import java.util.ArrayList;
import math.Angle;
import math.Precision;
import model.map.Map;
import model.army.motion.CollisionManager;
import model.army.motion.SteeringMachine;
import tools.LogUtil;

/**
 *
 * @author Benoît
 */
public class Mover {
    public enum Heightmap {SKY, AIR, GROUND};
    public enum PathfindingMode {FLY, WALK};

    // final 
    Heightmap heightmap;
    PathfindingMode pathfindingMode;

    public final Movable movable;
    final Map map;
    SteeringMachine sm;
    CollisionManager cm;

    // variables
    public Point3D pos = Point3D.ORIGIN;
    public Point3D velocity = Point3D.ORIGIN;
    
    public double yaw = 0;
    public double desiredYaw = 0;
    
    public boolean hasMoved = false;
    
    public ArrayList<Mover> toAvoid = new ArrayList<>();
    public ArrayList<Mover> toFlockWith = new ArrayList<>();
    public ArrayList<Mover> toLetPass = new ArrayList<>();
    
    
    public FlowField flowfield;
    private boolean hasDestination;
    public boolean hasFoundPost;
    public boolean holdPosition = false;
    public boolean tryHold = false;
    
    public Mover(Map map, Movable movable, Point3D position){
        this.map = map;
        this.movable = movable;
        pos = position;
        cm = new CollisionManager(this, map);
        sm = new SteeringMachine(this);
    }
    
    public void updatePosition(double elapsedTime) {
        double lastYaw = yaw;
        Point3D lastPos = new Point3D(pos);
        
        if(!holdPosition){
            Point3D steering = sm.getSteeringAndReset(elapsedTime);
            cm.applySteering(steering, elapsedTime, toAvoid);
        }
        head(elapsedTime);
        
        hasMoved = lastYaw != yaw || !lastPos.equals(pos);
        if(hasMoved)
            updateElevation();
        
        if(hasDestination)
            hasFoundPost = false;
        else {
            hasFoundPost = true;
            for(Mover m : toFlockWith)
                if(m.hasDestination){
                    hasFoundPost = false;
                }
        }
        if(!tryHold)
            holdPosition = false;
    }
    
    public void tryToHoldPositionSoftly(){
        tryHold = true;
        if(fly())
            holdPosition = true;
        else {
            ArrayList<Mover> all = new ArrayList<>();
            all.addAll(toAvoid);
            all.addAll(toFlockWith);
            all.addAll(toLetPass);
            for(Mover m : all)
                if(collide(m))
                    return;
            for(Mover m : toFlockWith)
                if(m.tryHold && !m.holdPosition)
                    return;
            holdPosition = true;
        }
    }
    public void tryToHoldPositionHardly(){
        tryHold = true;
        if(fly())
            holdPosition = true;
        else {
            ArrayList<Mover> all = new ArrayList<>();
            all.addAll(toAvoid);
            all.addAll(toFlockWith);
            all.addAll(toLetPass);
            for(Mover m : all)
                if(m.holdPosition && collide(m))
                    return;
            holdPosition = true;
        }
    }
    
    public double getSpacing(Mover o) {
        return movable.getRadius()+o.movable.getRadius();
    }
    
    public void setDestination(FlowField ff){
        flowfield = ff;
        hasDestination = true;
        hasFoundPost = false;
    }
    
    public void setDestinationReached(){
        hasDestination = false;
        for(Mover m : toFlockWith)
            if(getDistance(m) < getSpacing(m)+toFlockWith.size()/20)
                m.hasDestination = false;
    }
    
    public boolean hasDestination(){
        return hasDestination;
    }
    
    public Point2D getDestination(){
        if(flowfield != null)
            return flowfield.destination;
        return null;
    }
    
    public double getDistance(Mover o) {
        return pos.getDistance(o.pos);
    }

    public BoundingCircle getBounds() {
        return new BoundingCircle(new Point2D(pos), movable.getRadius());
    }

    public boolean collide(ArrayList<AlignedBoundingBox> walls){
        BoundingCircle agentBounds = getBounds();
        for(AlignedBoundingBox wall : walls)
            if(agentBounds.collide(wall))
                return true;
        return false;
    }
    
    public boolean collide(Mover other){
        return getDistance(other) <= getSpacing(other);
    }
    
    public void head(double elapsedTime) {
        if(!velocity.isOrigin())
            desiredYaw = velocity.get2D().getAngle();

        if(!Angle.areSimilar(desiredYaw, yaw)){
            double diff = Angle.getOrientedDifference(yaw, desiredYaw);
            if(diff > 0)
                yaw += Math.min(diff, movable.getRotSpeed()*elapsedTime);
            else
                yaw -= Math.min(-diff, movable.getRotSpeed()*elapsedTime);
        } else
            yaw = desiredYaw;
    }

    public Point3D getVectorTo(Mover o) {
        return o.pos.getSubtraction(pos);
    }
    
    public void separate(){
        sm.applySeparation(toLetPass);
    }
    
    public void flock(){
        sm.applySeparation(toFlockWith);
//        sm.applyCohesion(neighbors);
//        sm.applyAlignment(neighbors);
    }
    
    public void seek(Mover target){
        flock();
        separate();
        sm.seek(target);

        ArrayList<Mover> toAvoidExceptTarget = new ArrayList<>(toAvoid);
        toAvoidExceptTarget.remove(target);
        sm.avoidHoldingUnits(toAvoidExceptTarget);
    }

    public void seek(Point3D position){
        flock();
        separate();
        sm.seek(position);
        sm.avoidHoldingUnits(toAvoid);
    }
    
    public void followPath() {
        flock();
        separate();
        sm.proceedToDestination();
        sm.avoidHoldingUnits(toAvoid);
    }
    

    public void followPath(Mover target) {
        flock();
        separate();
        sm.proceedToDestination();

        ArrayList<Mover> toAvoidExceptTarget = new ArrayList<>(toAvoid);
        toAvoidExceptTarget.remove(target);
        sm.avoidHoldingUnits(toAvoidExceptTarget);
    }
    
    void updateElevation(){
        if(heightmap == Heightmap.GROUND)
            pos = new Point3D(pos.x, pos.y, map.getGroundAltitude(pos.get2D())+0.25);
        else if(heightmap == Heightmap.SKY)
            pos = new Point3D(pos.x, pos.y, map.getTile(pos.get2D()).level+3);
            
    }
    
    public boolean fly(){
        return pathfindingMode == PathfindingMode.FLY;
    }
    
    public double getSpeed(){
        return movable.getSpeed();
    }
    
    public Point2D getPos2D(){
        return new Point2D(pos);
    }
}
