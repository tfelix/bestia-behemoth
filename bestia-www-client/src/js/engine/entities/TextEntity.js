import Entity from './Entity.js';
import Signal from '../../io/Signal.js';

/**
 * This entity displays a text inside the game engine.
 */
export default class ImageEntity extends Entity {
	constructor(ctx) {
		super(ctx);

		this._sprite = game.add.text(0, 0, '', style);
    this._sprite.anchor.set(0.5);

		this._sprite.events.onInputOver.add(this._onOverHandler, this);
		this._sprite.events.onInputOut.add(this._onOutHandler, this);
	}

	setText(text) {


	}

	/**
	 * Sets the style of text.
	 */
	setStyle(style) {

	}

	/**
	 * Stops rendering of this entity and removes it from the scene.
	 */
	remove() {

		this._sprite.destroy();

	}
}
