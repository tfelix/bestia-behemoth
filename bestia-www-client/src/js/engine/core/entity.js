/**
 * 
 * @param {Bestia.Engine.World}
 *            world - A instance of the bestia world holding parsed information
 *            and utility methods of the current map/world.
 * @param {String}
 *            ident - Identifier for the entity. This identifier is used to
 *            locate the sprite of the entity and the description file
 *            ([IDENT]_desc).
 */
Bestia.Engine.Entity = function(ident, game, world) {

	this.walkspeed = 1;

	/**
	 * Position in tile coordinates.
	 * 
	 * @public
	 * @property {Object}
	 */
	this.pos = {
		x : 0,
		y : 0
	};

	/**
	 * ID of the bestia for this entity. Note: Not all entities have ids. A map
	 * entity is decribed only by it UUID. However the pbid is needed for
	 * different reasons for accessing these entities. So it is stored.
	 * 
	 * @public
	 * @property {Number}
	 */
	this.pbid = 0;

	/**
	 * Entities have aswell a UUID with which they can be identified. This UUID
	 * exisits (unlike the player bestia id, or pbid) in EVERY entity.
	 * 
	 * @public
	 * @property {String}
	 */
	this.uuid = "";

	this.game = game;
	this.world = world;

	/**
	 * Shortcut to the tile size of the current loaded map. Used to position
	 * entities.
	 * 
	 * @private
	 * @property {Number}
	 */
	this._tileSize = world.properties.tileSize;

	/**
	 * Holds the information about the resources of this sprite/entity.
	 * Animation frames, sounds, standing images etc.
	 * 
	 * @property
	 */
	this.desc = null;

	/**
	 * The underlying sprite for the engine.
	 * 
	 * @public
	 * @property {String}
	 */
	this.sprite = null;

	this._init(ident);
};

/**
 * Loads the data into the entity given by an object.
 * 
 * @method Bestia.Engine.Entity#_init
 * @private
 * @param {Object}
 *            obj - Object describing the entity.
 */
Bestia.Engine.Entity.prototype._init = function(obj) {

	this.pbid = obj.pbid;

	// For now there is only one sprite. Might change.
	var spriteName = obj.s[0];

	this.desc = this.game.cache.getJSON(spriteName + '_desc');
	this.sprite = this.game.add.sprite(0, 0, spriteName, 'walk_down/001.png');

	this.setPos(obj.x, obj.y);

	// Add it invisible first.
	this.sprite.alpha = 1;

	// Set anchor to the middle of the sprite to the bottom.
	this.sprite.anchor.setTo(0.5, 1);
	this.sprite.scale.setTo(this.desc.scale);

	// Prepare the animations of the sprite.
	this.desc.animations.forEach(function(anim) {
		var frames = Phaser.Animation.generateFrameNames(anim.name + '/', anim.from, anim.to, '.png', 3);
		this.sprite.animations.add(anim.name, frames, anim.fps, true, false);
	}, this);

	this.sprite.frameName = 'walk_down/001.png';
};

/**
 * Calculates the duration in ms of the total walk of the given path. Depends
 * upon the relative walkspeed of the entity.
 * 
 * @private
 * @method Bestia.Engine.Entity#_getWalkDuration
 * @returns Total walkspeed in ms.
 */
Bestia.Engine.Entity.prototype._getWalkDuration = function(length, walkspeed) {
	// Usual walkspeed is 3 tiles / s -> 1/3 s/tile.
	return Math.round((1 / 3) * length / walkspeed * 1000);
};

/**
 * Sets the position in tile coordinates.
 * 
 * @method Bestia.Engine.Entity#setPos
 */
Bestia.Engine.Entity.prototype.setPos = function(x, y) {
	this.pos.x = x;
	this.pos.y = y;
	var cords = this.world.getPxXY(x, y);
	this.sprite.x = cords.x + this.sprite.width / 2;
	this.sprite.y = cords.y + this._tileSize;
};

/**
 * Moves the entity along a certain path. The path is an array with {x: INT, y:
 * INT} components.
 * 
 * @param {Array}
 *            path - Array of coordinate objects {x: INT, y: INT}.
 */
Bestia.Engine.Entity.prototype.moveTo = function(path) {
	var self = this;

	this.tween = this.game.add.tween(this.sprite);

	// Push current position of the entity (start) to the path aswell.
	path.unshift(this.pos);

	// Calculate coordinate arrays from path.
	path.forEach(function(ele, i) {
		// This is our current position. No need to move TO this positon.
		if (i === 0) {
			return;
		}

		var cords = self.world.getPxXY(ele.x, ele.y);

		// We go single tile steps.
		var duration = this._getWalkDuration(1, 1);
		var lastTile = path[i - 1];

		// Check if we go diagonal to adjust speed.
		var distance = (lastTile.x - ele.x) * (lastTile.x - ele.x) + (lastTile.y - ele.y) * (lastTile.y - ele.y);
		if (distance > 1) {
			// diagonal move. Multi with sqrt(2).
			duration *= 1.414;
		}

		// Calculate total amount of speed.
		this.tween.to({
			// x : cords.x + Math.abs(this.sprite.width) / 2,
			x : cords.x + 20,
			y : cords.y + this._tileSize
		}, duration, Phaser.Easing.Linear.None, false);
	}, this);

	this.tween.onChildComplete.addOnce(function(a, b) {
		this.pos = path[b.current - 1];
		var isLast = path.length === (b.current - 1);
		var nextAnim = this.getAnimationName(path[b.current], this.pos);
		this.playAnim(nextAnim, isLast);

		console.log("Moved to: " + this.pos.x + " - " + this.pos.y);
	}, this);

	this.tween.onComplete.addOnce(function() {
		var size = path.length;
		this.pos = path[size - 1];
		var nextAnim = this.getAnimationName(this.pos, path[size - 2], true);
		this.playAnim(nextAnim);
		console.log("Moved to: " + this.pos.x + " - " + this.pos.y);
	}, this);

	// Start first animation immediately.
	this.playAnim(this.getAnimationName(path[1], path[0]));
	this.tween.start();
};

/**
 * Returns the name for the sprite animation while it moves. This depends on the
 * direction of the movement which is determined by looking into the difference
 * vector of the current tile and the next tile.
 * 
 * @param {boolean}
 *            isStanding - (Optional) Flag if the animation direction name
 *            should be the standing variant.
 * @method Bestia.Engine.Entity#getAnimationName
 * @private
 * @return {String} Name of the animation to play.
 */
Bestia.Engine.Entity.prototype.getAnimationName = function(nextTile, curTile, isStanding) {

	isStanding = isStanding || false;

	var dX = nextTile.x - curTile.x;
	var dY = nextTile.y - curTile.y;

	if (dX === 0 && dY === -1) {
		// moving up.
		return (isStanding) ? 'stand_up' : 'walk_up';
	} else if (dX === 0 && dY === 1) {
		// moving down.
		return (isStanding) ? 'stand_down' : 'walk_down';
	} else if (dX === -1 && dY === 0) {
		// moving left.
		return (isStanding) ? 'stand_left' : 'walk_left';
	} else if (dX === 1 && dY === 0) {
		// moving right.
		return (isStanding) ? 'stand_right' : 'walk_right';
	} else if (dX === -1 && dY === -1) {
		// left up.
		return (isStanding) ? 'stand_left_up' : 'walk_left_up';
	} else if (dX === -1 && dY === 1) {
		return (isStanding) ? 'stand_down_left' : 'walk_down_left';
	} else if (dX === 1 && dY === -1) {
		return (isStanding) ? 'stand_right_up' : 'walk_right_up';
	} else if (dX === 1 && dY === 1) {
		return (isStanding) ? 'stand_down_right' : 'walk_down_right';
	}

	return 'stand_down';

};

/**
 * Stops a current movement.
 * 
 * @public
 * @method Bestia.Engine.Entity#stopMove
 */
Bestia.Engine.Entity.prototype.stopMove = function() {

	this.tween.stop();

};

/**
 * Removes an entity from the game.
 * 
 * @public
 * @method Bestia.Engine.Entity#remove
 */
Bestia.Engine.Entity.prototype.remove = function() {

	this.tween.stop();
	this.sprite.destroy();

};

/**
 * Plays a specific animation. If it is a walk animation then by the name of the
 * animation the method takes core of flipping the sprite for mirrored
 * animations. It also handles stopping running animations and changing between
 * them.
 * 
 * @public
 * @method Bestia.Engine.Entity#playAnim
 * @param {String}
 *            name - Name of the animation to play.
 */
Bestia.Engine.Entity.prototype.playAnim = function(name) {

	// Check if its a stand animation or still image.
	var prefix = name.substring(0, 5);
	var isSingle = false;

	if (prefix === 'stand') {
		var isStand = true;
		if (name === 'stand_right' || name === 'stand_right_up' || name === 'stand_down_right') {
			this.sprite.scale.x = -1 * this.desc.scale;
			// Show the left variant animation.
			name = name.replace('right', 'left');
		} else {
			this.sprite.scale.x = this.desc.scale;
		}
	} else {
		if (name === 'walk_left' || name === 'walk_left_back') {
			this.sprite.animations.play(name);
		}

		if (name === 'walk_right' || name === 'walk_down_right' || name == 'walk_right_up') {
			// for the right versions we must flip the sprite.
			this.sprite.scale.x = -1 * this.desc.scale;
			// Show the left variant animation.
			name = name.replace('right', 'left');
		} else {
			this.sprite.scale.x = this.desc.scale;
		}
	}

	// Stop the current animation.
	if (name !== this.sprite.animations.name) {
		this.sprite.animations.stop();
	}

	if (isSingle) {
		this.sprite.frameName = this.desc[name];
	} else {
		this.sprite.animations.play(name);
	}
};
