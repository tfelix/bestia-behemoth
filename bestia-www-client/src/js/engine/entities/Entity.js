import WorldHelper from '../map/WorldHelper.js';

/**
 * Base entity for display via the bestia engine/phaser. The class contains all
 * important position management function and methods.
 */
export default class Entity {
	
	constructor(ctx, id, uuid) {
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
		this.id = id;

		this._game = ctx.game;

		this._ctx = ctx;

		/**
		 * The underlying sprite for the engine.
		 * 
		 * @public
		 * @property {String}
		 */
		this._sprite = null;
	}
	
	appear() {
		// no op.
	}

	/**
	 * This will show and display the entity with the default appear animation.
	 */
	show() {
		// no op.
	}

	update() {
		// no op.
	}

	/**
	 * Shows death animation if any. The entity was killd/destroyed by
	 * whatsoever means.
	 */
	kill() {
		// Placeholder death "animation".
		this._sprite.tint = 0xffffff;
		this._game.add.tween(this._sprite).to({alpha: 0}, 400, 'Linear', true);
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
			throw "Group must be instance of Phaser.Group";
		}

		if (this._sprite === null) {
			console.warn('addToGroup: Sprite is still null. Was not loaded/set yet.');
			return;
		}

		group.add(this._sprite);
	}

	_syncSpritePosition() {
		// Correct the sprite position.
		if (this._sprite !== null) {
			var pos = WorldHelper.getSpritePxXY(this._position.x, this._position.y);

			this._sprite.x = pos.x;
			this._sprite.y = pos.y;
		}
	}

	setPosition(x, y, noSync) {
		this._position.x = x;
		this._position.y = y;

		if (!noSync) {
			this._syncSpritePosition();
		}
	}
	
	get position() {
		return this._position;
	}
	
	set position(value) {
		value.x = value.x || 0;
		value.y = value.y || 0;

		this._position = value;
		this._syncSpritePosition();
	}

	/**
	 * Give access to the underlying sprite phaser API.
	 */
	get sprite() {
		return this._sprite;
	}
	
	/**
	 * Readonly access to the uuid.
	 */
	get uuid() {
		return this._uuid;
	}
	
	/**
	 * Returns the position of the entity in pixel in world space.
	 */
	get positionPx() {
		return this._sprite.position;
	}
	
	set positionPx(value) {
		value.x = value.x || 0;
		value.y = value.y || 0;

		this._sprite.postion = value;
	}
}

