import WorldHelper from '../map/WorldHelper.js';
import NOOP from '../../util/NOOP';

/**
 * Base entity for display via the bestia engine/phaser. It is a thin wrapper to
 * the phaser API.
 */
export default class Entity {
	
	constructor(ctx, id) {
		/**
		 * Position in tile coordinates.
		 * 
		 * @public
		 * @property {Object}
		 */
		this._position = {
			x : 0,
			y : 0
		};

		/**
		 * Each entity which exists on the server has an id. This id is globally
		 * unique and must be used when requesting certain action with an
		 * entity.
		 * 
		 * @public
		 * @property {String}
		 */
		this._id = 'NOID' || id;

		this._game = ctx.game;

		this._ctx = ctx;
		
		/**
		 * This function is called when the mouse is hovered over the main
		 * representation of this object.
		 */
		this.onInputOver = NOOP;
		
		/**
		 * This function is called when the mouse is hovered over the main
		 * representation of this object.
		 */
		this.onInputOut = NOOP;
		
		/**
		 * This callback is called if the user performs a primary click on the
		 * main representation of this object.
		 */
		this.onInputClick = NOOP;
	}
	
	/**
	 * Helper method which will attach all supported callbacks to the given game
	 * object.
	 */
	_setupCallbacks(gameObj) {
		gameObj.input.enabled = true;
		
		gameObj.events.onInputOver.add(x => { this.onInputOver(); });
		gameObj.events.onInputOut.add(x => { this.onInputOut(); });
		gameObj.events.onInputDown.add(x => { this.onInputClick(); })
	}
	
	/**
	 * Helper method to put the given sprite to the same (tile based) position
	 * as this entity.
	 */
	_syncSpritePosition(gameObj) {
		// Correct the sprite position.
		let pos = WorldHelper.getSpritePxXY(this._position.x, this._position.y);
		gameObj.x = pos.x;
		gameObj.y = pos.y;
	}
	
	/**
	 * Returns the root visual representation of this sprite. Should be
	 * overwritten in child implementations.
	 */
	getRootVisual() {
		throw 'Must be overridden by child implementations';
	}

	/**
	 * This function is called every tick in the animation loop and can be used
	 * to update internal sprite information. Especially in a multipart sprite
	 * object this can be useful.
	 */
	tickAnimation() {
		// no op.
	}

	/**
	 * Removes an entity from the game.
	 * 
	 * @public
	 * @method Bestia.Engine.BasicEntity#remove
	 */
	remove() {

		if(this._sprite !== null) {
			this._sprite.destroy();
		}

	}

	addToGroup(group) {
		if (!(group instanceof Phaser.Group)) {
			throw 'Group must be instance of Phaser.Group';
		}

		if (this._sprite === null) {
			console.warn('addToGroup: Sprite is still null. Was not loaded/set yet.');
			return;
		}

		group.add(this._sprite);
	}

	setPosition(x, y) {
		this._position.x = x || 0;
		this._position.y = y || 0;
	}
	
	getPosition() {
		return this._position;
	}
	
	/**
	 * Returns the size of the visual representation of the entity.
	 */
	getSize() {
		return {x: 0, y: 0};
	}
	
	/**
	 * Give access to the underlying sprite phaser API.
	 */
	get sprite() {
		return this._sprite;
	}
	
	/**
	 * Readonly access to the eid.
	 */
	get id() {
		return this._id;
	}
	
	/**
	 * Returns the position of the entity in pixel in world space.
	 */
	getPositionPx() {
		return this._sprite.position;
	}
	
	/**
	 * Sets the position in pixel in world space.
	 */
	setPositionPx(x, y) {
		value.x = x || 0;
		value.y = y || 0;

		this._sprite.postion = value;
	}
}

