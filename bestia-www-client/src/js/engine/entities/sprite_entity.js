Bestia.Engine.SpriteEntity = function(game, uuid, x, y, playerBestiaId) {
	Bestia.Engine.BasicEntity.call(this, game);

	this.uuid = uuid;
	this.setPosition(x, y);

	/**
	 * Holds a list with names of supported animations. This is a cache and is
	 * generated from the JSON sprite description.
	 * 
	 * @private
	 * @property {Array}
	 */
	this._availableAnimationNames = [];

	if (playerBestiaId !== undefined) {
		this.playerBestiaId = playerBestiaId;
	}
};

Bestia.Engine.SpriteEntity.prototype = Object.create(Bestia.Engine.BasicEntity.prototype);
Bestia.Engine.SpriteEntity.prototype.constructor = Bestia.Engine.SpriteEntity;

Bestia.Engine.SpriteEntity.prototype.setSprite = function(spriteName) {
	// Save the description data for reference. This is done here because now we
	// are sure all the data has been loaded.
	this.data = this._game.cache.getJSON(spriteName + '_desc');

	// Generate the animation names.
	this._availableAnimationNames = this.data.animations.map(function(val) {
		return val.name;
	});

	this._sprite = this._game.add.sprite(0, 0, spriteName, 'walk_down/001.png');

	// Set anchor to the middle of the sprite to the bottom.
	this._sprite.anchor = this.data.anchor;
	this._sprite.scale.setTo(this.data.scale);
	this._sprite.alpha = 0;

	// Add the multi sprites if there are some of them.
	var multisprites = this.data.multiSpriteAnchors;
	if (Array.isArray(multisprites)) {
		for (var i = 0; i < multisprites.length; i++) {
			var ms = multisprites[i];

			var sprite = this._sprite.addChild(this._game.make.sprite(ms.defaultAnchor.x, ms.defaultAnchor.y, ms.id));
			sprite.anchor.setTo(0.5, 1);
			sprite.scale.setTo(ms.scale);
			sprite.frameName = 'bottom.png';
		}
	}

	// Register all the animations of the sprite.
	this.data.animations.forEach(function(anim) {
		var frames = Phaser.Animation.generateFrameNames(anim.name + '/', anim.from, anim.to, '.png', 3);
		this._sprite.animations.add(anim.name, frames, anim.fps, true, false);
	}, this);

	this._sprite.frameName = 'walk_down/001.png';

	// Re-set position so the sprite gets now postioned.
	var pos = this.position;
	this.setPosition(pos.x, pos.y);
};

/**
 * Calculates the duration in ms of the total walk of the given path. Depends
 * upon the relative walkspeed of the entity.
 * 
 * @private
 * @method Bestia.Engine.Entity#_getWalkDuration
 * @returns Total walkspeed in ms.
 */
Bestia.Engine.SpriteEntity.prototype._getWalkDuration = function(length, walkspeed) {
	// Usual walkspeed is 3 tiles / s -> 1/3 s/tile.
	return Math.round((1 / 3) * length / walkspeed * 1000);
};

Bestia.Engine.SpriteEntity.prototype.show = function() {
	this._sprite.alpha = 1;
};

Bestia.Engine.SpriteEntity.prototype.appear = function() {
	this._sprite.alpha = 1;
};

/**
 * Sprite position is updated with the data from the server.
 */
Bestia.Engine.SpriteEntity.prototype.update = function(msg) {
	var x = msg.x - this.position.x;
	var y = msg.y - this.position.y;
	var distance = Math.sqrt(x * x + y * y);

	// Directly set distance if too far away.
	if (distance > 1.5) {
		this.setPosition(msg.x, msg.y);
		return;
	}

	this.moveTo([ {
		x : msg.x,
		y : msg.y
	} ]);
};

/**
 * Can be used to position related child sprites (like weapons or a head) frame
 * by frame.
 */
Bestia.Engine.SpriteEntity.prototype.tickAnimation = function() {

};

/**
 * Stops rendering of this entity and removes it from the scene.
 */
Bestia.Engine.SpriteEntity.prototype.remove = function() {

	this._tween.stop();
	this._sprite.destroy();

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
Bestia.Engine.SpriteEntity.prototype.playAnimation = function(name) {

	// If the animation is the same. Just let it run.
	if (name === this._sprite.animations.name) {
		return;
	}
	
	// We need to mirror the sprite for right sprites.
	if (name.indexOf("right") !== -1) {
		this._sprite.scale.x = -1 * this.data.scale;
		name = name.replace('right', 'left');
	} else {
		this._sprite.scale.x = this.data.scale;
	}

	// Check if the sprite "knows" this animation. If not we have several
	// fallback strategys to test before we fail.
	if (!this._hasAnimationName(name)) {
		name = this._getAnimationFallback(name);
		
		if (name === null) {
			console.warn("Could not found alternate animation solution for: " + name);
			return;
		}
	}

	this._sprite.animations.play(name);
};

/**
 * Tests if the entity sprite supports a certain animation name. It gets a bit
 * complicated since some animations are implemented purely in software (right
 * walking a mirror of left walking).
 * 
 * @param name
 */
Bestia.Engine.SpriteEntity.prototype._hasAnimationName = function(name) {
	if (name.indexOf('right') !== -1) {
		name = name.replace('right', 'left');
	}

	return this._availableAnimationNames.indexOf(name) !== -1;
};

/**
 * Tries to find a alternate animation. If no supported animation could be
 * found, we will return null.
 * 
 * @private
 * @return {String} - Newly found animation to display, or NULL if no suitable
 *         animation could been found.
 */
Bestia.Engine.SpriteEntity.prototype._getAnimationFallback = function(name) {
	if (name === "stand_down_left" || name === "stand_left") {
		// Try to replace it with stand down.
		if (this._availableAnimationNames.indexOf("stand_down") === -1) {
			return null;
		} else {
			return "stand_down";
		}
	}

	if (name == "stand_left_up") {
		// Try to replace it with stand down.
		if (this._availableAnimationNames.indexOf("stand_up") === -1) {
			return null;
		} else {
			return "stand_up";
		}
	}
	
	if(name == "stand_right") {
		// Try to replace it with stand down.
		if (this._availableAnimationNames.indexOf("stand_up") === -1) {
			return null;
		} else {
			return "stand_up";
		}
	}
	
	return null;
};

/**
 * Stops a current movement.
 * 
 * @public
 * @method Bestia.Engine.Entity#stopMove
 */
Bestia.Engine.SpriteEntity.prototype.stopMove = function() {

	if(this.tween) {
		this.tween.stop();
	}
	
};

/**
 * Moves the entity along a certain path. The path is an array with {x: INT, y:
 * INT} components. The path must not contain the current position of the
 * entity.
 * 
 * @param {Array}
 *            path - Array of coordinate objects {x: INT, y: INT}.
 */
Bestia.Engine.SpriteEntity.prototype.moveTo = function(path) {
	
	this.stopMove();

	this.tween = this._game.add.tween(this._sprite);

	// Push current position of the entity (start) to the path aswell.
	path.unshift(this.position);

	// Calculate coordinate arrays from path.
	path.forEach(function(ele, i) {
		// This is our current position. No need to move TO this positon.
		if (i === 0) {
			return;
		}

		var cords = Bestia.Engine.World.getSpritePxXY(ele.x, ele.y);

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
			x : cords.x,
			y : cords.y
		}, duration, Phaser.Easing.Linear.None, false);
	}, this);

	this.tween.onChildComplete.addOnce(function(a, b) {

		var pos = path[b.current - 1];
		var isLast = path.length === (b.current - 1);
		var nextAnim = this._getWalkAnimationName(pos, path[b.current]);
		this.playAnimation(nextAnim, isLast);
		console.log("Moved to: " + pos.x + " - " + pos.y);

	}, this);

	this.tween.onComplete.addOnce(function() {

		var size = path.length;
		var pos = path[size - 1];
		var nextAnim = this._getStandAnimationName(path[size - 2], pos, true);
		this.playAnimation(nextAnim);
		console.log("Moved to: " + pos.x + " - " + pos.y);

	}, this);

	// Start first animation immediately.
	this.playAnimation(this._getWalkAnimationName(path[0], path[1]));
	this.tween.start();
};

/**
 * Returns the animation name for walking to this position, from the old
 * position.
 * 
 * @param oldPos
 * @param newPos
 */
Bestia.Engine.SpriteEntity.prototype._getWalkAnimationName = function(oldTile, newTile) {
	var x = newTile.x - oldTile.x;
	var y = newTile.y - oldTile.y;

	if (x === 0 && y === -1) {
		return "walk_up";
	} else if (x === 1 && y === -1) {
		return "walk_right_up";
	} else if (x === 1 && y === 0) {
		return "walk_right";
	} else if (x === 1 && y === 1) {
		return "walk_down_right";
	} else if (x === 0 && y === 1) {
		return "walk_down";
	} else if (x === -1 && y === 1) {
		return "walk_down_left";
	} else if (x === -1 && y === 0) {
		return "walk_left";
	} else {
		return "walk_left_up";
	}
};

/**
 * Returns the animation name for standing to this position.
 * 
 * @param oldPos
 * @param newPos
 */
Bestia.Engine.SpriteEntity.prototype._getStandAnimationName = function(oldTile, newTile) {
	var x = newTile.x - oldTile.x;
	var y = newTile.y - oldTile.y;
	
	if (x === 0 && y === -1) {
		return "stand_up";
	} else if (x === 1 && y === -1) {
		return "stand_right_up";
	} else if (x === 1 && y === 0) {
		return "stand_right";
	} else if (x === 1 && y === 1) {
		return "stand_down_right";
	} else if (x === 0 && y === 1) {
		return "stand_down";
	} else if (x === -1 && y === 1) {
		return "stand_down_left";
	} else if (x === -1 && y === 0) {
		return "stand_left";
	} else {
		return "stand_left_up";
	}
};