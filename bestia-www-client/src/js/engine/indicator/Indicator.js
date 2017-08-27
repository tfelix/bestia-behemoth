import WorldHelper from '../map/WorldHelper.js';
import groups, {GROUP_LAYERS} from '../core/Groups';
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
			throw new Error('Manager can not be null.');
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
		
		groups.get(GROUP_LAYERS.SPRITES_BOTTOM).add(this._marker);
	}

	deactivate() {
		this._ctx.game.input.deleteMoveCallback(this._onMouseMove, this);
		this._ctx.game.input.onDown.remove(this._onClick, this);
		this._ctx.groups.spritesUnder.remove(this._marker);
	}

	/**
	 * Checks if this indicator can be overwritten by the new one. Usually this
	 * is the default behaviour.
	 * 
	 * @param {Indicator}
	 *            indicator - The new indicator intended to override the
	 *            currently active one.
	 */
	allowOverwrite(indicator) {
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
	 * Private shortcut method to request itself as an active indicator.
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
