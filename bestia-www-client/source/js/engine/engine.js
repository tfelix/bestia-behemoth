this.Bestia = this.Bestia || {};
/**
 * Main message module. Responsible for sending messages to the server and to
 * receive them and rebroadcast them into the client.
 * 
 * @module Bestia.Engine
 */
(function(app, $, createjs, ko, Phaser) {

	'use strict';
	
	var Engine = {};
	
	var platforms;
	var layer;
	
	Engine.marker = {
		gfx: null,
		updateMarker : function() {
			Engine.marker.gfx.x = layer.getTileX(game.input.activePointer.worldX) * 32;
			Engine.marker.gfx.y = layer.getTileY(game.input.activePointer.worldY) * 32;
		}
	}

	// Phaser Teil.
	var game = new Phaser.Game(800, 600, Phaser.AUTO, 'bestia-canvas', {
		preload : preload,
		create : create,
		update : update
	});

	function preload() {
		game.load.spritesheet('novi', 'assets/sprites/mob/novice.png', 40, 75);
	}

	function create() {
		
		game.physics.startSystem(Phaser.Physics.ARCADE);
		
		layer = map.createLayer('Tile Layer 1');
	    layer.resizeWorld();
		
		//  Our painting marker
	    /*
	    Engine.marker.gfx = game.add.graphics();
	    Engine.marker.gfx.lineStyle(2, 0xffffff, 1);
	    Engine.marker.gfx.drawRect(0, 0, 32, 32);
	    game.input.addMoveCallback(Engine.marker.updateMarker, this);*/
		
		var novi = game.add.sprite(300, 200, 'novi');
		novi.animations.add('left', [0, 1, 2, 3], 10, true);
		
		novi.animations.play('left');
		
		//  The platforms group contains the ground and the 2 ledges we can jump on
	    platforms = game.add.group();

	    //  We will enable physics for any object that is created in this group
	    platforms.enableBody = true;

	    // Here we create the ground.
	    var ground = platforms.create(0, game.world.height - 64, 'ground');

	    //  Scale it to fit the width of the game (the original sprite is 400x32 in size)
	    ground.scale.setTo(2, 2);

	    //  This stops it from falling away when you jump on it
	    ground.body.immovable = true;

	    //  Now let's create two ledges
	    var ledge = platforms.create(400, 400, 'ground');

	    ledge.body.immovable = true;

	    ledge = platforms.create(-150, 250, 'ground');

	    ledge.body.immovable = true;
	}

	function update() {
	}

})(Bestia, jQuery, createjs, ko, Phaser);
