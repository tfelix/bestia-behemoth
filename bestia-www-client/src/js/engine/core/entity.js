/**
 * 
 * @param {Bestia.Engine.World}
 *            world - A instance of the bestia world holding parsed information
 *            and utility methods of the current map/world.
 */
Bestia.Engine.Entity = function(game, world) {
	var self = this;
	this.walkspeed = 1;
	// TODO Das Bestia selection system ausweiten und ausbessern.
	this.pbid = 2;

	this.game = game;
	this.world = world;

	this.path = [];

	/**
	 * Holds the information about the resources of this sprite/entity.
	 * Animation frames, sounds, standing images etc.
	 * 
	 * @property
	 */
	this.desc = game.cache.getJSON('mastersmith_desc');

	// Initialize the sprite.
	this.sprite = game.add.sprite(128, 128, 'mastersmith', 'walk_down/001.png');
	// Todo das funktioniert nur wenn die sprites ca 2 tiles groß sind. besser
	// wäre 0,1 und dann halt durch koordinaten steuern.
	this.sprite.anchor.setTo(0, 1);
	this.sprite.scale.setTo(0.68);

	this.tween = game.add.tween(this.sprite);

	// Prepare the animations of the sprite.
	this.desc.animations.forEach(function(anim) {
		var frames = Phaser.Animation.generateFrameNames(anim.name + '/', anim.from, anim.to, '.png', 3);
		this.sprite.animations.add(anim.name, frames, anim.fps, true, false);
	}, this);

	// play stand animation.
	// this.sprite.animations.play('stand');
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

Bestia.Engine.Entity.prototype.setTo = function(x, y) {
	var cords = this.world.getPxXY(x, y + 1);
	this.sprite.x = cords.x;
	this.sprite.y = cords.y;
};

Bestia.Engine.Entity.prototype.moveTo = function(path) {
	var self = this;

	this.tween = this.game.add.tween(this.sprite);

	// Get current position.
	var curPosT = this.world.getTileXY(this.sprite.x, this.sprite.y);

	// Calculate coordinate arrays from path.
	var lastTile = curPosT;
	var animationOrder = [];
	path.forEach(function(ele, i) {
		var cords = self.world.getPxXY(ele.x, ele.y);
		cords.y += this.world.properties.tileSize;

		// We go single tile steps.
		var duration = this._getWalkDuration(1, 1);

		// Check if we go diagonal.
		var distance = (lastTile.x - ele.x) * (lastTile.x - ele.x) + (lastTile.y - ele.y) * (lastTile.y - ele.y);
		if (distance > 1) {
			// diagonal move. Multi with sqrt(2).
			duration *= 1.414;
		}

		// Calculate total amount of speed.
		this.tween.to({
			x : cords.x,
			y : cords.y,
		}, duration, Phaser.Easing.Linear.None, false);

		animationOrder.push(this.getAnimationName(ele, lastTile));

		// Set last tile.
		lastTile = ele;
	}, this);

	this.tween.onChildComplete.addOnce(function(a, b) {
		this.playAnim(animationOrder[b.current]);

	}, this);

	// Start first animation immediately.
	this.playAnim(this.getAnimationName(curPosT, path[0]));
	this.tween.start();
};

/**
 * Returns the name for the sprite animation while it moves. This depends on the
 * direction of the movement which is determined by looking into the difference
 * vector of the current tile and the next tile.
 * 
 * @method Bestia.Engine.Entity#getAnimationName
 * @private
 * @return {String} Name of the animation to play.
 */
Bestia.Engine.Entity.prototype.getAnimationName = function(nextTile, curTile) {

	var dX = nextTile.x - curTile.x;
	var dY = nextTile.y - curTile.y;

	if (dX == 0 && dY == -1) {
		// moving up.
		return 'walk_up';
	} else if (dX == 0 && dY == 1) {
		// moving down.
		return 'walk_down';
	} else if (dX == -1 && dY == 0) {
		return 'walk_left';
	} else if (dX == 1 && dY == 0) {
		return 'walk_right';
	} else if (dX == -1 && dY == -1) {
		// left up.
		return 'walk_left_up';
	} else if (dX == -1 && dY == 1) {
		return 'walk_down_left';
	} else if (dX == -1 && dY == 1) {
		return 'walk_right_up';
	} else if (dX == 1 && dY == 1) {
		return 'walk_down_right';
	}

	return 'walk_left_up';

};

/**
 * Stops a current movement.
 * 
 * @method Bestia.Engine.Entit#stopMove
 */
Bestia.Engine.Entity.prototype.stopMove = function() {

	this.tween.stop();

};

/**
 * 
 */
Bestia.Engine.Entity.prototype.playAnim = function(name) {

	// Check if its a stand animation or still image.
	var prefix = name.substring(0, 5);

	if (prefix === 'stand') {
		if (name === 'stand_right' || name === 'stand_right_up' || name === 'stand_down_right') {
			this.sprite.scale.x = -0.68;
			// Show the left variant animation.
			name = name.replace('right', 'left');
		} else {
			this.sprite.scale.x = 0.68;
		}

		this.sprite.frameName = this.desc[name];
		return;
	}

	if (name === 'walk_left' || name === 'walk_left_back') {
		this.sprite.animations.play(name);
	}

	if (name === 'walk_right' || name === 'walk_down_right' || name == 'walk_right_up') {
		// for the right versions we must flip the sprite.
		this.sprite.scale.x = -0.68;
		// Show the left variant animation.
		name = name.replace('right', 'left');
	} else {
		this.sprite.scale.x = 0.68;
	}

	this.sprite.animations.play(name);
};
