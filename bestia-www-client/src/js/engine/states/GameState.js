/*global Phaser */

import * as AStar from '../plugins/AStar';
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

		this._marker = null;

		this._ctx = context;
	}
	
	preload() {
		
	}
	
	create() {
		/**
		 * Phaser whipes the scene graph when states change. Thus one need to init
		 * the groups when the final (game_state) is started.
		 */
		// Groups can be created.
		this._ctx.groups = {};
		this._ctx.groups.mapGround = this.game.add.group(undefined, 'map_ground');
		this._ctx.groups.sprites = this.game.add.group(undefined, 'sprites');
		this._ctx.groups.mapOverlay = this.game.add.group(undefined, 'map_overlay');
		this._ctx.groups.effects = this.game.add.group(undefined, 'fx');
		this._ctx.groups.overlay = this.game.add.group(undefined, 'overlay');
		this._ctx.groups.gui = this.game.add.group(undefined, 'gui');

		
		// ==== PLUGINS ====
		var astar = this.game.plugins.add(Phaser.Plugin.AStar);

		// @ifdef DEVELOPMENT
		this.game.plugins.add(Phaser.Plugin.Debug);
		this.game.stage.disableVisibilityChange = true;
		// @endif
		// ==== /PLUGINS ====
		
		// Trigger fx create effects.
		this._ctx.fxManager.create();
		this._ctx.indicatorManager.create();

		// Load the tilemap and display it.
		// this.ctx.zone = new World(this.game, astar, this.ctx.groups);
		// this.ctx.zone.loadMap(this.ctx.playerBestia.location());
		
		// Activate move handler.
		this._ctx.indicatorManager.showDefault();
		
		// ========= TESTING =========
		this.game.world.setBounds(0, 0, 800, 600);
		
		
		this.sprite = this.game.add.sprite(400, 300, 'poring');

		
		this.cursor = this.game.input.keyboard.createCursorKeys();
		this.sprite.anchor.setTo(0,0);
		this.game.physics.startSystem(Phaser.Physics.ARCADE);
		this.game.camera.follow(this.sprite);
		this.extended = false;

		this._ctx.renderer.tile.playerSprite = this.sprite;
		this._ctx.renderer.tile.clearDraw();
		
		//this.ctx.entityUpdater.releaseHold();
		this._ctx.pubsub.publish(Signal.ENGINE_GAME_STARTED);
	}

	update() {
		
		this._ctx.renderer.tile.update();

		
		// Trigger the update effects.
		this._ctx.fxManager.update();

		// Update the animation frame groups of all multi sprite entities.
		/*
		 * var entities = this.ctx.entityCache.getAllEntities();
		 * entities.forEach(function(entity) { entity.tickAnimation(); });
		 */
		
		// Group sort the sprite layer.
		this._ctx.groups.sprites.sort('y', Phaser.Group.SORT_ASCENDING);
	}
	
	render() {
		// @ifdef DEVELOPMENT
		this.game.debug.cameraInfo(this.game.camera, 32, 32);
		this.game.debug.spriteCoords(this.sprite, 32, 500);
		// @endif
	}

	shutdown() {

		// We need to UNSUBSCRIBE from all subscriptions to avoid leakage.
		// TODO Ich weiß nicht ob das hier funktioniert oder ob referenz zu
		// callback
		// benötigt wird.
		// this.pubsub.unsubscribe(Bestia.Signal.ENGINE_CAST_ITEM,
		// this._onCastItem.bind(this));
		
		this._ctx.clear();
	}

}