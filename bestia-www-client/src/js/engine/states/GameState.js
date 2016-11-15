/*global Phaser */

import Signal from '../../io/Signal.js';
import World from '../map/World.js';
import WorldHelper from '../map/WorldHelper';
import TileRenderer from '../renderer/TileRenderer';

/**
 * Central game state for controlling the games logic.
 * 
 * @constructor
 * @class Bestia.Engine.States.GameState
 * @param {Bestia.Engine}
 *            engine - Reference to the bestia engine.
 */
export default class GameState {
	
	constructor(context) {

		this.marker = null;

		this._ctx = context;
		
		/**
		 * Flag to tag the render as dirty. This will trigger a rather expensive
		 * redrawing of the scene. This should be set if the player moves.
		 */
		this._isMapDirty = true;
	}
	
	create() {
		
		// ==== PLUGINS ====
		// var astar = this.game.plugins.add(Phaser.Plugin.AStar);

		// @ifdef DEVELOPMENT
		this.game.plugins.add(Phaser.Plugin.Debug);
		// @endif
		// ==== /PLUGINS ====
		
		// this._ctx.createGroups();
		
		// Trigger fx create effects.
		// this._ctx.fxManager.create();
		// this._ctx.indicatorManager.create();

		// Load the tilemap and display it.
		// this.ctx.zone = new World(this.game, astar, this.ctx.groups);
		// this.ctx.zone.loadMap(this.ctx.playerBestia.location());

		// @ifdef DEVELOPMENT
		this.game.stage.disableVisibilityChange = true;
		// @endif

		// this.ctx.pubsub.publish(Signal.ENGINE_GAME_STARTED);
		// this.ctx.entityUpdater.releaseHold();
		
		// Activate move handler.
		// this.ctx.indicatorManager.showDefault();
		
		// ========= TESTING =========
		//this.game.world.setBounds(0, 0, 1024, 768);
		
		
		// TILEMAP TEST 1ms
		
		
		// Draw tiles.
		
		
		/*
		var batch = this.game.add.spriteBatch();
		// Draw tiles.
		this.game.world.setBounds(0, 0, 4000, 4000);
		for(var x = 0; x < 90; x++) {
			for(var y = 0; y < 60; y++) {
				let sp = this.game.make.sprite(x*32, y*32, 'tilesheet', 47);
				batch.addChild(sp);
			}
		}*/
		
		this.sprite = this.game.add.sprite(400, 300, 'poring');
		this.cursor = this.game.input.keyboard.createCursorKeys();
		this.sprite.anchor.setTo(0,0);
		this.game.physics.startSystem(Phaser.Physics.ARCADE);
		this.game.camera.follow(this.sprite);
		this.extended = false;
		this._tileRenderer = new TileRenderer(this.game);
		this._tileRenderer.playerSprite = this.sprite;
		this._tileRenderer.clearDraw();
	}

	update() {
		
		// Redraw the scene.
		if(this.isMapDirty) {
			this.isMapDirty = false;
			
			// Get the tile coordinates which must be drawn.
			let x1 = this._ctx.playerBestia.posX - WorldHelper.SIGHT_RANGE_X;
			let x2 = this._ctx.playerBestia.posX + WorldHelper.SIGHT_RANGE_X;
			let y1 = this._ctx.playerBestia.posY - WorldHelper.SIGHT_RANGE_Y;
			let y2 = this._ctx.playerBestia.posY + WorldHelper.SIGHT_RANGE_Y;
		}
		
		this._tileRenderer.update();
		
		if (this.cursor.left.isDown)
	    {
			this.sprite.x -= 2;
	    }
	    else if (this.cursor.right.isDown)
	    {
	    	this.sprite.x += 2;
	    }

	    if (this.cursor.up.isDown)
	    {
	    	this.sprite.y -= 2;
	    }
	    else if (this.cursor.down.isDown)
	    {
	    	this.sprite.y += 2;
	    }
		
		// Trigger the update effects.
		// this.ctx.fxManager.update();

		// Update the animation frame groups of all multi sprite entities.
		/*
		 * var entities = this.ctx.entityCache.getAllEntities();
		 * entities.forEach(function(entity) { entity.tickAnimation(); });
		 */
		
		// Group sort the sprite layer.
		// this.ctx.groups.sprites.sort('y', Phaser.Group.SORT_ASCENDING);
	}
	
	render() {
		this.game.debug.cameraInfo(this.game.camera, 32, 32);
	    this.game.debug.spriteCoords(this.sprite, 32, 500);
		
	}

	shutdown() {

		// We need to UNSUBSCRIBE from all subscriptions to avoid leakage.
		// TODO Ich weiß nicht ob das hier funktioniert oder ob referenz zu
		// callback
		// benötigt wird.
		// this.pubsub.unsubscribe(Bestia.Signal.ENGINE_CAST_ITEM,
		// this._onCastItem.bind(this));

	}

}