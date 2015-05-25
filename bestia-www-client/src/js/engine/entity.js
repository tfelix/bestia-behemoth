Bestia.Engine.Entity = function(data, game) {

	var self = this;

	// data muss folgende infora enthalten:
	/*
	 * { name: 'poring' anims: [name: 'stand', from: 1, to: 4, frameRate: 5]
	 * 
	 * 
	 */

	// Initialize the sprite.
	this.sprite = game.add.sprite(500, 120, 'poring', 'stand/001.png');

	// poring.scale.setTo(0.5,0.5);

	// We support basically 4 walk animations (walk_left, walk_left_back,
	// walkt_right and walk_right_back, where the 2 latter ones are mirrored
	// from the first ones).

	// add animation phases
	data.anims.forEach(function(ele) {

		self.sprite.animations.add(ele.name, Phaser.Animation.generateFrameNames(ele.name + '/', ele.from, ele.to,
				'.png', 3), ele.frameRate, true, false);
	});

	/*
	 * poring1.animations.add('stand_01', [ 'stand/001.png', 'stand/002.png',
	 * 'stand/003.png', 'stand/004.png' ], 5, true, false);
	 * poring1.animations.add('walk_left_back',
	 * Phaser.Animation.generateFrameNames('walk_back/', 1, 8, '.png', 3), 5,
	 * true, false); poring1.animations.add('walk_left', [
	 * Phaser.Animation.generateFrameNames('walk/', 1, 8, '.png', 3) ], 5, true,
	 * false); poring2.animations.add('stand_01', [ 'stand/001.png',
	 * 'stand/002.png', 'stand/003.png', 'stand/004.png' ], 5, true, false);
	 * poring3.animations.add('stand_01', [ 'stand/001.png', 'stand/002.png',
	 * 'stand/003.png', 'stand/004.png' ], 5, true, false);
	 */

	// play stand animation.
	this.sprite.animations.play('stand');
};

// An entity has several position properties. It has the concrete tile position
// on which it resides. Nether the less an entity can be much bigger then one
// tile and occupy more. For this reason there is a "hitbox" describing the
// collision. The property getCenterX and getCenterY will get the center
// position of this entity in px. getSizeX() and getSizeY() will also return the
// value in px and give the size of the entity.

Bestia.Engine.Entity.prototype = {
	get getCenterX() {
		return 100;
	}
};

Bestia.Engine.Entity.prototype.constructor = Bestia.Engine.Entity;

Bestia.Engine.Entity.prototype.playAnim = function(name) {
	if (name === 'walk_left' || name === 'walk_left_back') {
		this.sprite.animations.play(name);
	}

	// for the right versions we must flip the sprite.
	this.sprite.scale = -1;

	if (name === 'walk_right') {
		this.sprite.animations.play('walk_left');
	}

	this.sprite.animations.play('walk_left_back');
};

/**
 * Lets the entity take damage. It will display a damage animation and reduce
 * the internal HP of the entity.
 * 
 * @param {Object}
 *            damage - Bestia damage JSON object.
 */
Bestia.Engine.Entity.prototype.takeDamage = function(damage) {

};

Bestia.Engine.Entity.preload = function() {
	// ATLAS
	this.load.atlasJSONHash('poring', 'assets/sprite/mob/poring.png', 'assets/sprite/mob/poring.json');
};
