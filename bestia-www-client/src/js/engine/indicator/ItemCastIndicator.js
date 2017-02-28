import WorldHelper from '../map/WorldHelper.js';
import Indicator from './Indicator.js';
import Signal from '../../io/Signal.js';
import World from '../map/World.js';

/**
 * Visualize the casting of an item. If the position was determined it will
 * publish this information.
 */
export default class ItemCast extends Indicator {
	constructor(manager) {
		super(manager);

		/**
		 * Holds the castable item for the callback.
		 * 
		 * @private
		 * @property
		 */
		this._item = null;

		// Listen for activation signal.
		manager.ctx.pubsub.subscribe(Signal.ENGINE_CAST_ITEM, this._onCastItem.bind(this));
	}
	
	_onClick(pointer) {

		if (pointer.button === Phaser.Mouse.RIGHT_BUTTON) {
			// Was canceled.
			this._manager.showDefault();
			return;
		}

		// Publish the cast information.
		var pointerCords = WorldHelper.getTileXY(pointer.worldX, pointer.worldY);
		this._manager.ctx.pubsub.publish(Signal.INVENTORY_PERFORM_CAST, {
			item : this._item,
			cords : pointerCords
		});

		// Forfeit control.
		this._manager.showDefault();
	}

	_onCastItem(_, item) {
		// Change the size of the indicator based on the item size.
		this._parseIndicator(item.indicator);

		this._item = item;

		// Asks to get activated.
		this._setActive();
	}

	/**
	 * Extract Upper.
	 */
	_parseIndicator(indicatorStr) {
		var tokens = indicatorStr.split(':');

		/*
		 * Currently there is only the circle. switch (tokens[0]) { //case
		 * 'circle': default: // Currently there is only the circle. break; }
		 */
		
		// Adjust the scale to match the fields.
		// Must scale back to 1 to get the right pixel width.
		this._marker.scale.setTo(1);
		var currentCells = this._marker.width / World.TILE_SIZE;
		var scale = tokens[1] / currentCells;
		
		this._marker.scale.setTo(scale);
	}

	/**
	 * Preload all needed assets.
	 */
	load() {
		this._ctx.game.load.image('cast_indicator', this._ctx.url.getIndicatorUrl('cast_indicator'));
	}

	/**
	 * Preload all needed assets.
	 */
	create() {
		this._marker = this._ctx.game.make.sprite(500, 500, 'cast_indicator');
		// this._ctx.groups.overlay.add(this._marker);
		this._marker.anchor.setTo(0.5, 0.5);
		this._marker.angle = 0;
		this._marker.alpha = 0.7;
		this._ctx.game.add.tween(this._marker).to({
			angle : 360
		}, 1500, Phaser.Easing.Linear.None, true, 0).loop(true);
	}
}