package controller.battlefield;

import model.CommandManager;
import model.battlefield.army.ArmyManager;
import view.MapView;
import view.math.Translator;

import com.google.common.eventbus.Subscribe;
import com.jme3.app.state.AppStateManager;
import com.jme3.input.InputManager;
import com.jme3.renderer.Camera;

import controller.Controller;
import controller.cameraManagement.IsometricCameraManager;
import de.lessvoid.nifty.Nifty;
import event.EventManager;
import event.InputEvent;
import geometry.geom2d.Point2D;

/**
 *
 */
public class BattlefieldController extends Controller {
	private boolean paused = false;


	public BattlefieldController(MapView view, Nifty nifty, InputManager inputManager, Camera cam) {
		super(view, inputManager, cam);
		this.view = view;
		inputInterpreter = new BattlefieldInputInterpreter(this);
		guiController = new BattlefieldGUIController(nifty, this);

		EventManager.register(this);

		cameraManager = new IsometricCameraManager(cam, 10);
	}

	@Override
	public void update(float elapsedTime) {
		// draw selection rectangle
		Point2D selStart = ((BattlefieldInputInterpreter)inputInterpreter).selectionStart;
		if(selStart != null){
			Point2D p = Translator.toPoint2D(inputManager.getCursorPosition());
			view.drawSelectionArea(selStart, p);
			((BattlefieldInputInterpreter)inputInterpreter).updateSelection();
		} else {
			view.getGuiNode().detachAllChildren();
		}

		// update selectables
		CommandManager.updateSelectables(spatialSelector.getCenterViewCoord(view.getRootNode()));
		guiController.update();

		// udpdate army
		if(!paused) {
			ArmyManager.update(elapsedTime);
		}
	}

	@Subscribe
	public void manageEvent(InputEvent ev) {
		guiController.update();

	}

	// TODO: See AppState.setEnabled => use it, this is a better implementation
	public void togglePause(){
		paused = !paused;
		view.getActorManager().pause(paused);
	}

	@Override
	public void stateAttached(AppStateManager stateManager) {
		super.stateAttached(stateManager);
		inputManager.setCursorVisible(true);
		guiController.activate();
	}

}
