import WorldHelper from '../map/WorldHelper.js';
import IndicatorManager from './IndicatorManager.js';

/**
 * Basic indicator for visualization of the mouse pointer. This visualization is
 * changed depending on in which state of the game the user is. While using an
 * item for example or an attack the appearence of the pointer will change.
 * 
 */
export default class Indicator {

	constructor(manager) {
		if (!(manager instanceof IndicatorManager)) {
			throw new Error("Manager can not be null.");
		}

		this._ctx = manager.ctx;
		
		this._manager = manager;

		this._marker = null;
	}
	
	activate() {
		// Move the marker to the current active mouse position.
		this._onMouseMove();

		this._ctx.game.input.addMoveCallback(this._onMouseMove, this);
		this._ctx.game.input.onDown.add(this._onClick, this);
		this._ctx.game.world.add(this._marker);
	}

	deactivate() {
		this._ctx.game.input.deleteMoveCallback(this._onMouseMove, this);
		this._ctx.game.input.onDown.remove(this._onClick, this);
		this._ctx.game.world.remove(this._marker);
	}

	/**
	 * Checks if this indicator can be overwritten by the new one.
	 */
	get allowOverwrite() {
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
	load() {
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
	 * Private shurtcut method to request itself as an active indicator.
	 */
	_setActive() {
		return this._manager.requestActive(this);
	}

	/**
	 * Mouse pointer move callback. Is fired if the game receives a moving mouse
	 * pointer. This operates only if a _marker is set and will move its
	 * coordiantes centered on a tile.
	 */
	_onMouseMove() {
		if (this._marker === null) {
			return;
		}

		var pointer = this._ctx.game.input.activePointer;

		// From px to tiles and back.
		var cords = WorldHelper.getTileXY(pointer.worldX, pointer.worldY);
		WorldHelper.getPxXY(cords.x, cords.y, cords);

		this._marker.x = cords.x;
		this._marker.y = cords.y;

		// TODO Check if we are on an non walkable tile. Hide cursor here.
	}
	
}
