/*global Phaser */

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
		this._ctx.groups.spritesUnder = this.game.add.group(undefined, 'sprites_under');
		this._ctx.groups.sprites = this.game.add.group(undefined, 'sprites');
		this._ctx.groups.spritesOver = this.game.add.group(undefined, 'sprites_over');
		this._ctx.groups.mapOverlay = this.game.add.group(undefined, 'map_overlay');
		this._ctx.groups.effects = this.game.add.group(undefined, 'fx');
		this._ctx.groups.overlay = this.game.add.group(undefined, 'overlay');
		this._ctx.groups.gui = this.game.add.group(undefined, 'gui');


		// ==== PLUGINS ====
		// @ifdef DEVELOPMENT
		this.game.plugins.add(Phaser.Plugin.Debug);
		this.game.stage.disableVisibilityChange = true;
		// @endif	
		// ==== /PLUGINS ====
		
		// Trigger fx create effects.
		this._ctx.fxManager.create();
		this._ctx.indicatorManager.create();
		
		// Activate move handler.
		this._ctx.indicatorManager.showDefault();
		
		// ========= TESTING =========
		this.game.world.setBounds(0, 0, 800, 600);
		
		// After all is setup create the player sprite.
		let pb = this._ctx.playerBestia;
		let playerData = {eid: pb.entityId(), x: pb.posX(), y: pb.posY(), s: {s: pb.sprite(), t: pb.spriteType()}, a: 'APPEAR'};
		this._ctx.entityFactory.build(playerData, function(playerEntity){
			// Follow the player
			console.log('Player build');
			this._ctx.playerEntity = playerEntity;
			this.game.camera.follow(playerEntity.sprite);
			this._ctx.pubsub.publish(Signal.ENGINE_GAME_STARTED);
		}.bind(this));
		
		console.log('Draw called');
		this._tileRender.clearDraw();
		this._ctx.entityUpdater.releaseHold();
	}

	update() {
		
		// Calls the renderer.
		this._ctx.render.update();

		// Trigger the update effects.
		this._ctx.fxManager.update();
		
		this._ctx.indicatorManager.update();

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