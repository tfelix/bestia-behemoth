Bestia.Engine.SpriteEntity = function(game, uuid, x, y, playerBestiaId) {
	Bestia.Engine.BasicEntity.call(this, game);

	this.uuid = uuid;
	this.setPosition(x, y);

	if (playerBestiaId !== undefined) {
		this.playerBestiaId = playerBestiaId;
	}
};

Bestia.Engine.SpriteEntity.prototype = Object.create(Bestia.Engine.BasicEntity.prototype);
Bestia.Engine.SpriteEntity.prototype.constructor = Bestia.Engine.SpriteEntity;

Bestia.Engine.SpriteEntity.prototype.setSprite = function(spriteName) {
	// Save the description data for reference.
	this.data = this._game.cache.getJSON(spriteName + '_desc');
	
	this._sprite = this._game.add.sprite(0, 0, spriteName, 'walk_down/001.png');

	// Set anchor to the middle of the sprite to the bottom.
	this._sprite.anchor.setTo(0.5, 1);
	this._sprite.scale.setTo(this.data.scale);
	this._sprite.alpha = 0;

	// Add the multi sprites.
	var multisprites = this.data.multiSpriteAnchors;
	for(var i = 0; i < multisprites.length; i++) {
		var ms = multisprites[i];
		
		var sprite = this._sprite.addChild(this._game.make.sprite(ms.defaultAnchor.x, ms.defaultAnchor.y, ms.id));
		sprite.anchor.setTo(0.5, 1);
		sprite.scale.setTo(ms.scale);
		sprite.frameName = 'bottom.png';
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

Bestia.Engine.SpriteEntity.prototype.update = function() {

};

Bestia.Engine.SpriteEntity.prototype.tickAnimation = function() {
	
};

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

	this._sprite.animations.play(name);
};

/**
 * Stops a current movement.
 * 
 * @public
 * @method Bestia.Engine.Entity#stopMove
 */
Bestia.Engine.SpriteEntity.prototype.stopMove = function() {

	this.tween.stop();

};

/**
 * Moves the entity along a certain path. The path is an array with {x: INT, y:
 * INT} components.
 * 
 * @param {Array}
 *            path - Array of coordinate objects {x: INT, y: INT}.
 */
Bestia.Engine.SpriteEntity.prototype.moveTo = function(path) {

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