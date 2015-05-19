/**
 * Central game state for controlling the games logic.
 * 
 * @constructor
 * @class Bestia.Engine.States.GameState
 */
Bestia.Engine.States.GameState = function() {
	this.marker = null;
	this.groundLayer = null;
};

Bestia.Engine.States.GameState.prototype = {
		
		
	preload : function() {
		// TODO Sollte ausgelagert werden in den Loading State.
		this.load.image('logo', 'assets/img/logo_small.png');
		this.load.tilemap('map', 'assets/map/test-zone1/test-zone1.json', null, Phaser.Tilemap.TILED_JSON);
		this.load.image('tiles', 'assets/map/test-zone1/tilemap1.png');

		// Sprites.
		this.load.image('1_F_ORIENT_01', 'assets/sprite/1_F_ORIENT_01.png');
		this.load.image('1_M_BARD', 'assets/sprite/1_M_BARD.png');

		this.load.audio('bg_theme', 'assets/sound/theme/prontera_fields.mp3');

		// ATLAS
		this.load.atlasJSONHash('poring', 'assets/sprite/mob/poring.png', 'assets/sprite/mob/poring.json');
	},

	create : function() {
		var map = this.game.add.tilemap('map');
		map.addTilesetImage('Berge', 'tiles');
		this.groundLayer = map.createLayer('Boden');
		this.groundLayer.resizeWorld();
		map.createLayer('Berge');

		// Our painting marker
		this.marker = this.game.add.graphics();
		this.marker.lineStyle(2, 0xffffff, 1);
		this.marker.drawRect(0, 0, 32, 32);

		this.game.input.addMoveCallback(this.updateMarker, this);

		// Music.
		// this.game.add.audio('bg_theme').play();

		// Single Sprites
		this.game.add.sprite(160, 320, '1_F_ORIENT_01');
		this.game.add.sprite(320, 320, '1_M_BARD');

		// create atlas
		var poring1 = this.game.add.sprite(500, 120, 'poring', 'stand/001.png');
		var poring2 = this.game.add.sprite(320, 90, 'poring', 'stand/001.png');
		var poring3 = this.game.add.sprite(150, 160, 'poring', 'stand/001.png');
		// poring.scale.setTo(0.5,0.5);

		// add animation phases
		poring1.animations.add('stand_01', [ 'stand/001.png', 'stand/002.png', 'stand/003.png', 'stand/004.png' ],
				5, true, false);
		poring1.animations.add('walk_left', [ 'walk/001.png', 'walk/002.png', 'walk/003.png', 'walk/004.png',
				'walk/005.png', 'walk/006.png', 'walk/007.png', 'walk/008.png' ], 5, true, false);
		poring2.animations.add('stand_01', [ 'stand/001.png', 'stand/002.png', 'stand/003.png', 'stand/004.png' ],
				5, true, false);
		poring3.animations.add('stand_01', [ 'stand/001.png', 'stand/002.png', 'stand/003.png', 'stand/004.png' ],
				5, true, false);

		// play animation
		poring1.animations.play('walk_left');
		poring2.animations.play('stand_01');
		poring3.animations.play('stand_01');

		// Text test.
		var style = {
			font : "18px Arial",
			fill : "#ffffff",
			align : "center",
			stroke : '#000000',
			strokeThickness : 3
		};

		var text = this.game.add.text(500, 600, "123", style);
		var tween = this.game.add.tween(text).to({
			x : [ 475, 450 ],
			y : [ 510, 615 ]
		}, 1000);
		tween.interpolation(function(v, k) {
			return Phaser.Math.bezierInterpolation(v, k);
		});
		tween.repeat(Infinity);
		this.game.add.tween(text).to({
			alpha : 0
		}, 100, Phaser.Easing.Linear.None, true, 900).start();
		tween.start();
	},

	update : function() {

	},
	
	updateMarker : function() {

		this.marker.x = this.groundLayer.getTileX(this.game.input.activePointer.worldX) * 32;
		this.marker.y = this.groundLayer.getTileY(this.game.input.activePointer.worldY) * 32;

	}
};

Bestia.Engine.States.GameState.prototype.constructor = Bestia.Engine.States.GameState;