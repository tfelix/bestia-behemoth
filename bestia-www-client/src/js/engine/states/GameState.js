/*global Phaser */

import Signal from '../../io/Signal.js';
import World from '../map/World.js';
import WorldHelper from '../map/WorldHelper';

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
		var map = this.game.add.tilemap();
		map.addTilesetImage('tilemap');
		var layer = map.create('ground', 40, 30, 32, 32);
		layer.resizeWorld();
		
		// Draw tiles.
		for(var x = 0; x < 40; x++) {
			for(var y = 0; y < 30; y++) {
				map.putTile(30, x, y, 'ground');
			}
		}
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
			
			for(let y = y1; y < y2; y++) {
				for(let x = x1; x < x2; x++) {
					// Find the tile under this space.
					let tiles = this._ctx.mapManager.getTileGids(x, y);
					tiles.foreach(function(gid, layer){
						// Iterate over each layer.
						this._ctx.tilesetManager.get
					});
				}
			}
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

	shutdown() {

		// We need to UNSUBSCRIBE from all subscriptions to avoid leakage.
		// TODO Ich weiß nicht ob das hier funktioniert oder ob referenz zu
		// callback
		// benötigt wird.
		// this.pubsub.unsubscribe(Bestia.Signal.ENGINE_CAST_ITEM,
		// this._onCastItem.bind(this));

	}

}