import Entity from './Entity.js';
import Signal from '../../io/Signal.js';

/**
 * This entity displays a text inside the game engine.
 */
export default class TextEntity extends Entity {
	constructor(ctx, style) {
		super(ctx);

		style = style || {};

		this._sprite = ctx.game.make.text(0, 0, '', style);
		this._sprite.anchor.set(0.5);
	}

	/**
	 * Returns the visual at the root of the internal group.
	 */
	getRootVisual() {
		return this._sprite;
	}

	/**
	 * Sets the text of the entity.
	 */
	setText(text) {
		if(this._sprite) {
			this._sprite.setText(text);
		}
	}

	/**
	 * Sets the style of text.
	 */
	setStyle(style) {
		if(this._sprite) {
			this._sprite.setStyle(style);
		}
	}

	/**
	 * Stops rendering of this entity and removes it from the scene.
	 */
	remove() {

		this._sprite.destroy();

	}
}
