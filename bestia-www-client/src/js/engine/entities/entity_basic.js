Bestia.Engine.BasicEntity = function(game) {

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
 * This function is called every tick in the animation loop and can be used to
 * update internal sprite information. Especially in a multipart sprite object
 * this can be useful.
 */
Bestia.Engine.BasicEntity.prototype.tickAnimation = function() {
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

Bestia.Engine.BasicEntity.prototype._syncSpritePosition = function() {
	// Correct the sprite position.
	if (this._sprite !== null) {
		var pos = Bestia.Engine.World.getSpritePxXY(this._position.x, this._position.y);

		this._sprite.x = pos.x;
		this._sprite.y = pos.y;
	}
};

Bestia.Engine.BasicEntity.prototype.setPosition = function(x, y) {
	this._position.x = x;
	this._position.y = y;

	this._syncSpritePosition();
};

Object.defineProperty(Bestia.Engine.BasicEntity.prototype, 'position', {

	get : function() {
		return this._position;
	},

	set : function(value) {

		value.x = value.x || 0;
		value.y = value.y || 0;

		this._position = value;
		this._syncSpritePosition();
	}

});

/**
 * Returns the position in pixel in the world space.
 */
Object.defineProperty(Bestia.Engine.BasicEntity.prototype, 'positionPixel', {

	get : function() {
		return this._sprite.position;
	},

	set : function(value) {

		value.x = value.x || 0;
		value.y = value.y || 0;

		this._sprite.postion = value;
	}

});
