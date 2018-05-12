import { PointerManager } from "./PointerManager";
import { EngineContext } from "../EngineContext";
import { Point } from "../../entities/Point";
import { Px } from "../../entities/Px";

/**
 * Basic indicator for visualization of the mouse pointer. This visualization is
 * changed depending on in which state of the game the user is. While using an
 * item for example or an attack the appearence of the pointer will change.
 * 
 */
export class Pointer {

	constructor(
    protected readonly manager: PointerManager, 
    protected readonly ctx: EngineContext
  ) {
	}
	
	activate() {
    // no op.
	}

	deactivate() {
		// no op.
	}

	/**
	 * Checks if this indicator can be overwritten by the new one. Usually this
	 * is the default behaviour.
	 * 
	 * @param {Indicator} indicator - The new indicator intended to override the currently active one.
	 */
	allowOverwrite(otherPointer: Pointer) {
		return true;
	}

	/**
	 * Override an create all needed game objects here.
	 */
	create() {
		// no op.
	}

	/**
	 * Overwrite to load all needed assets in order to draw this indicator.
	 */
	load(loader: Phaser.Loader.LoaderPlugin) {
		// no op.
	}

	/**
	 * If there are static assets which the indicator needs one can load them in
	 * here. The method is called by the system before the general operation of
	 * the engine starts.
	 */
	preLoadAssets() {
		// no op.
	}

	/**
	 * Private shortcut method to request itself as an active indicator.
	 */
	protected setSelfActive() {
		return this.manager.requestActive(this);
  }
  
  updatePosition(point: Point, px: Px) {
    // no op.
  }
}