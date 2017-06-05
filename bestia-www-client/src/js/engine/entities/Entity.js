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
			x: 0,
			y: 0
		};

		/**
		 * Each entity which exists on the server has an id. This id is globally
		 * unique and must be used when requesting certain action with an
		 * entity.
		 * 
		 * @public
		 * @property {long}
		 */
		this._id = id || -1;

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
	 * object. These are indirectly calling the externalized, public callbacks
	 * which can be altered later on without rewiring this calls.
	 */
	_setupCallbacks(gameObj) {
		gameObj.inputEnabled = true;

		gameObj.events.onInputOver.add(() => { this.onInputOver(); });
		gameObj.events.onInputOut.add(() => { this.onInputOut(); });
		gameObj.events.onInputDown.add(() => { this.onInputClick(); })
	}

	/**
	 * Returns the root visual representation of this sprite. Should be
	 * overwritten in child implementations.
	 */
	getRootVisual() {
		throw 'getRootVisual() must be overridden by child implementations';
	}

	/**
	 * Adds the entity to the game stage in order to render it.
	 */
	addToGame() {
		this._game.add.existing(this.getRootVisual());
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

		if (this._sprite !== null) {
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
		let visual = this.getRootVisual();

		if (visual) {
			let pos = WorldHelper.getSpritePxXY(x, y);
			visual.x = pos.x;
			visual.y = pos.y;
		}
	}

	/**
	 * Gets the current position in tile space.
	 */
	getPosition() {
		let visual = this.getRootVisual();

		if (!visual) {
			return { x: 0, y: 0 };
		}

		return WorldHelper.getTileXY(visual.position.x, visual.position.y);
	}

	/**
	 * Returns the size of the visual representation of the entity.
	 */
	getSize() {
		let visual = this.getRootVisual();
		if (!visual) {
			return { width: 0, height: 0 };
		}

		return { width: visual.width, height: visual.height };
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
		let visual = this.getRootVisual();

		if (visual) {
			visual.x = x;
			visual.y = y;
		}
	}
}

