Bestia.Engine.Entity = function() {
	// create atlas
	var poring1 = this.game.add.sprite(500, 120, 'poring', 'stand/001.png');
	var poring2 = this.game.add.sprite(320, 90, 'poring', 'stand/001.png');
	var poring3 = this.game.add.sprite(150, 160, 'poring', 'stand/001.png');
	// poring.scale.setTo(0.5,0.5);

	// add animation phases
	poring1.animations.add('stand_01', [ 'stand/001.png', 'stand/002.png', 'stand/003.png', 'stand/004.png' ], 5,
			true, false);
	poring1.animations.add('walk_left_back', Phaser.Animation.generateFrameNames('walk_back/', 1, 8, '.png', 3), 5, true, false);
	poring1.animations.add('walk_left', [Phaser.Animation.generateFrameNames('walk/', 1, 8, '.png', 3) ], 5, true, false);
	poring2.animations.add('stand_01', [ 'stand/001.png', 'stand/002.png', 'stand/003.png', 'stand/004.png' ], 5,
			true, false);
	poring3.animations.add('stand_01', [ 'stand/001.png', 'stand/002.png', 'stand/003.png', 'stand/004.png' ], 5,
			true, false);

	// play animation
	poring1.animations.play('walk_left_back');
	poring2.animations.play('stand_01');
	poring3.animations.play('stand_01');
	
};

Bestia.Engine.Entity.preload = function() {
	// ATLAS
	this.load.atlasJSONHash('poring', 'assets/sprite/mob/poring.png', 'assets/sprite/mob/poring.json');
};
