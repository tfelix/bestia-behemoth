Bestia.Engine.BasicEntity = function(ctx) {

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

	this._game = ctx.game;
	
	this._ctx = ctx;

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

	this._sprite.destroy();

};

Bestia.Engine.BasicEntity.prototype.addToGroup = function(group) {
	if (!(group instanceof Phaser.Group)) {
		throw "Group must be instance of Phaser.Group";
	}

	if (this._sprite === null) {
		console.warn('addToGroup: Sprite is still null. Was not loaded/set yet.');
		return;
	}

	group.add(this._sprite);
};

Bestia.Engine.BasicEntity.prototype._syncSpritePosition = function() {
	// Correct the sprite position.
	if (this._sprite !== null) {
		var pos = Bestia.Engine.World.getSpritePxXY(this._position.x, this._position.y);

		this._sprite.x = pos.x;
		this._sprite.y = pos.y;
	}
};

Bestia.Engine.BasicEntity.prototype.setPosition = function(x, y, noSync) {
	this._position.x = x;
	this._position.y = y;

	if(!noSync) {
		this._syncSpritePosition();
	}
};

Bestia.Engine.BasicEntity.prototype._getDistance = function(pos1, pos2) {
	var x = pos1.x - pos2.x;
	var y = pos1.y - pos2.y;

	return Math.sqrt(x * x + y * y);
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
