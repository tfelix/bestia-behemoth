import Entity from './Entity.js';
import World from '../core/World.js';


export default class ItemEntity extends Entity {
	constructor(ctx, uuid, x, y, spriteName) {
		super(ctx);
		
		spriteName = spriteName || 'default_item';
	
		this.uuid = uuid;
		this._game = ctx.game;
	
		var pos = World.getPxXY(x + 0.5, y + 0.5);
		
		this._sprite = this._game.add.sprite(pos.x, pos.y, spriteName);
		this._sprite.alpha = 0;
	
		// bottom left of the item.
		this._sprite.anchor.setTo(0.5);
	}

	show() {
		this._sprite.alpha = 1;
	}
	
	/**
	 * An item will drop to the ground from approx. 1.5m from above. So it has to
	 * start 1.5 tiles "higher" then the landing tile.
	 */
	appear() {

		// Set the start position.
		var pos = World.getPxXY(this._position.x, this._position.y - 1.5);
		var endY = this._sprite.y;
		this._sprite.y = pos.y;

		this._sprite.alpha = 1;
		this._game.add.tween(this._sprite).to({
			y : endY
		}, 250, Phaser.Easing.Linear.None, true);
	}
}
