/*global Phaser */

import * as AStar from '../plugins/phaser_pathfinding-0.2.0';
import Signal from '../../io/Signal.js';
import TileRender from '../renderer/TileRenderer';

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
	
	create() {
		
		// ==== VAR SETUP ====
		this._tileRender = this._ctx.render.getRender(TileRender.NAME);
		
		/**
		 * Phaser whipes the scene graph when states change. Thus one need to
		 * init the groups when the final (game_state) is started.
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
		// @ifdef DEVELOPMENT
		this.game.plugins.add(Phaser.Plugin.Debug);
		this.game.stage.disableVisibilityChange = true;
		// @endif
		
		// Nicht sauber.
		let pathfinder = this.game.plugins.add(Phaser.Plugin.PathFinderPlugin);
		this._ctx.etc.pathfinder = pathfinder;
		var walkable = [];
		for(let y = 0; y < 32; y++) {
			var row = [];
			for(let x = 0; x < 32; x++) {
				row.push(0);
			}
			walkable.push(row);
		}
		walkable[10][3] = 1;
		walkable[10][4] = 1;
		walkable[10][5] = 1;
		walkable[10][6] = 1;
		walkable[10][7] = 1;
		pathfinder.setGrid(walkable, [0]);
		// ==== /PLUGINS ====
		
		// Trigger fx create effects.
		this._ctx.fxManager.create();
		this._ctx.indicatorManager.create();
		
		// Activate move handler.
		this._ctx.indicatorManager.showDefault();
		
		// ========= TESTING =========
		this.game.world.setBounds(0, 0, 800, 600);

		
		// this.sprite.anchor.setTo(0,0);
		// this.game.physics.startSystem(Phaser.Physics.ARCADE);
		// this.game.camera.follow(this.sprite);
		// this.extended = false;

		this._tileRender.playerSprite = this.sprite;
		this._tileRender.clearDraw();
		
		this._ctx.entityFactory.build({uuid: 1, x: 10, y: 10, s: 'mastersmith', a: 'APPEAR', t: 'PLAYER_ANIM'});
		
		// this.ctx.entityUpdater.releaseHold();
		this._ctx.pubsub.publish(Signal.ENGINE_GAME_STARTED);
	}

	update() {
		
		// Calls the renderer.
		this._ctx.render.update();

		// Trigger the update effects.
		this._ctx.fxManager.update();

		// Update the animation frame groups of all multi sprite entities.
		let entities = this._ctx.entityCache.getAllEntities();
		entities.forEach(function(entity) { 
			entity.tickAnimation(); 
		});
		
		// Group sort the sprite layer.
		this._ctx.groups.sprites.sort('y', Phaser.Group.SORT_ASCENDING);
	}
	
	render() {
		// @ifdef DEVELOPMENT
		this.game.debug.cameraInfo(this.game.camera, 32, 32);
		// this.game.debug.spriteCoords(this.sprite, 32, 500);
		// @endif
	}

	shutdown() {

		// We need to UNSUBSCRIBE from all subscriptions to avoid leakage.
		
		this._ctx.clear();
	}

}