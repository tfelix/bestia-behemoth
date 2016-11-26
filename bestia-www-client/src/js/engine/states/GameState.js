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
	}
	
	preload() {
		
	}
	
	create() {
		
		// ==== PLUGINS ====
		// var astar = this.game.plugins.add(Phaser.Plugin.AStar);

		// @ifdef DEVELOPMENT
		this.game.plugins.add(Phaser.Plugin.Debug);
		this.game.stage.disableVisibilityChange = true;
		// @endif
		// ==== /PLUGINS ====
		
		// this._ctx.createGroups();
		
		// Trigger fx create effects.
		// this._ctx.fxManager.create();
		// this._ctx.indicatorManager.create();

		// Load the tilemap and display it.
		// this.ctx.zone = new World(this.game, astar, this.ctx.groups);
		// this.ctx.zone.loadMap(this.ctx.playerBestia.location());


		// this.ctx.pubsub.publish(Signal.ENGINE_GAME_STARTED);
		// this.ctx.entityUpdater.releaseHold();
		
		// Activate move handler.
		//this.ctx.indicatorManager.showDefault();
		
		// ========= TESTING =========
		this.game.world.setBounds(0, 0, 800, 600);
		
		
		this.sprite = this.game.add.sprite(400, 300, 'poring');
		
		//var blur = this.game.add.filter('Blur', null, this.game.cache.getShader('blur'));
		//var blur = new Phaser.Filter(this.game, null, this.game.cache.getShader('blur'));
		//this.sprite.filters = [blur];
		
		this.cursor = this.game.input.keyboard.createCursorKeys();
		this.sprite.anchor.setTo(0,0);
		this.game.physics.startSystem(Phaser.Physics.ARCADE);
		this.game.camera.follow(this.sprite);
		this.extended = false;

		this._ctx.renderer.tile.playerSprite = this.sprite;
		this._ctx.renderer.tile.clearDraw();
	}

	update() {
		
		this._ctx.renderer.tile.update();
		
		/*
		 * if (this.cursor.left.isDown) { this.sprite.x -= 2; } else if
		 * (this.cursor.right.isDown) { this.sprite.x += 2; }
		 * 
		 * if (this.cursor.up.isDown) { this.sprite.y -= 2; } else if
		 * (this.cursor.down.isDown) { this.sprite.y += 2; }
		 */
		
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