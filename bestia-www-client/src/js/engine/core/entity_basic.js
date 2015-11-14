Bestia.Engine.BasicEntity = function(game, x, y) {

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
	 * Entities have aswell a UUID with which they can be identified. This UUID
	 * exisits (unlike the player bestia id, or pbid) in EVERY entity which is
	 * spawned in the system.
	 * 
	 * @public
	 * @property {String}
	 */
	this.uuid = "";

	this._game = game;

	/**
	 * The underlying sprite for the engine.
	 * 
	 * @public
	 * @property {String}
	 */
	this._sprite = null;

	this.position = {
		x : x,
		y : y
	};
};

Bestia.Engine.BasicEntity.prototype.appear = function() {
	// no op.
};

Bestia.Engine.BasicEntity.prototype.show = function() {
	// no op.
};

Bestia.Engine.BasicEntity.prototype.update = function() {
	// no op.
};

/**
 * Removes an entity from the game.
 * 
 * @public
 * @method Bestia.Engine.BasicEntity#remove
 */
Bestia.Engine.BasicEntity.prototype.remove = function() {

	this.sprite.destroy();

};

Object.defineProperty(Bestia.Engine.BasicEntity.prototype, 'position', {

	get : function() {
		return this._position;
	},

	set : function(value) {

		value.x = value.x || 0;
		value.y = value.y || 0;

		this._position = value;
		if (this._sprite !== null) {
			var pos = this._world.getPxXY(value.x, value.y);
			this._sprite.x = pos.x;
			this._sprite.y = pos.y;
		}
	}

});
