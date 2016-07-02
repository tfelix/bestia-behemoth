import Entity from './Entity.js';
import Signal form '../../io/Signal.js';

export default class ImageEntity extends Entity {
	constructor(ctx, uuid, x, y, desc) {
		Bestia.Engine.BasicEntity.call(this, ctx, uuid);

		this._data = desc;

		this.setPosition(x, y);
	}
	
	setSprite = function(spriteName) {

		this._sprite = this._game.add.image(0, 0, spriteName);
		
		// Enable input.
		this._sprite.inputEnabled = true;
		
		this._sprite.events.onInputOver.add(this._onOverHandler, this);
		this._sprite.events.onInputOut.add(this._onOutHandler, this);

		this._setupSprite(this._sprite, this._data);

		// Re-set position so the sprite gets now postioned.
		this.setPosition(this.position.x, this.position.y);
	};

	_onOverHandler() {
		this._ctx.pubsub.publish(Signal.ENGINE_REQUEST_INDICATOR, {handle: 'basic_attack_over', entity: this});
	}

	_onOutHandler() {
		this._ctx.pubsub.publish(Signal.ENGINE_REQUEST_INDICATOR, {handle: 'basic_attack_out', entity: this});
	}

	/**
	 * Helper function to setup a sprite with all the information contained inside a
	 * description object.
	 * 
	 * @param sprite
	 * @param descObj
	 */
	_setupSprite(sprite, descObj) {

		// Setup the normal data.
		sprite.anchor = descObj.anchor || {
			x : 0.5,
			y : 1
		};

		// Sprite is invisible at first.
		sprite.alpha = 0;
	}

	show() {
		this._sprite.alpha = 1;
	}

	appear() {
		this._sprite.alpha = 1;
	}

	/**
	 * Sprite position is updated with the data from the server.
	 */
	update(msg) {
		var x = msg.x - this.position.x;
		var y = msg.y - this.position.y;
		var distance = Math.sqrt(x * x + y * y);

		// Directly set distance if too far away.
		if (distance > 1.5) {
			this.setPosition(msg.x, msg.y);
			return;
		}
		
		this.setPosition(msg.x, msg.y);
	}

	/**
	 * Can be used to position related child sprites (like weapons or a head) frame
	 * by frame.
	 */
	update() {
		// TODO
	}

	/**
	 * Stops rendering of this entity and removes it from the scene.
	 */
	remove() {

		this._sprite.destroy();

	}
}
