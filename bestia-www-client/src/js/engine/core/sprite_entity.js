Bestia.Engine.SpriteEntity = function(game, uuid, x, y, spriteName) {
	Bestia.Engine.BasicEntity.call(this, game, x, y);
	
	this.uuid = uuid;

	this.data = this._game.cache.getJSON(spriteName + '_desc');
	this._sprite = this._game.add.sprite(0, 0, spriteName, 'walk_down/001.png');
	// Set anchor to the middle of the sprite to the bottom.
	this._sprite.anchor.setTo(0.5, 1);
	this._sprite.scale.setTo(this.data.scale);
	this._sprite.alpha = 0;
	
	// Add the head
	var head = this._sprite.addChild(game.make.sprite(0, -66, 'female_01'));
	head.anchor.setTo(0.5, 1);
	head.scale.setTo(1.2);
	head.frameName = 'bottom.png';

	// bottom left of the item.
	this._sprite.anchor.setTo(0.5, 1);
	
	this.setPosition(x, y);	

	// Register all the animations of the sprite.
	this.data.animations.forEach(function(anim) {
		var frames = Phaser.Animation.generateFrameNames(anim.name + '/', anim.from, anim.to, '.png', 3);
		this._sprite.animations.add(anim.name, frames, anim.fps, true, false);
	}, this);

	this._sprite.frameName = 'walk_down/001.png';
};

Bestia.Engine.SpriteEntity.prototype = Object.create(Bestia.Engine.BasicEntity.prototype);
Bestia.Engine.SpriteEntity.prototype.constructor = Bestia.Engine.SpriteEntity;

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

	// Check if its a stand animation or still image.
	var prefix = name.substring(0, 5);
	var isSingle = false;

	if (prefix === 'stand') {
		//var isStand = true;
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