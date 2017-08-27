/*global Phaser */

import Signal from '../../io/Signal.js';
import Groups from '../core/Groups';
import TileRender from '../renderer/TileRenderer';
import WorldHelper from '../map/WorldHelper';
import LOG from '../../util/Log';
import PhaserDebug from '../plugins/phaser-debug';
import pathfinder from '../map/pathfinder';

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

		/**
		 * Phaser whipes the scene graph when states change. Thus one need to
		 * init the groups when the final (game_state) is started.
		 */
		// Groups can be created.
		Groups.initilize(this.game);

		// ==== PLUGINS ====
		// @ifdef DEVELOPMENT
		this.game.plugins.add(PhaserDebug);
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
		// ========= END TESTING =========


		// After all is setup create the player sprite.
		let pb = this._ctx.playerBestia;
		let playerData = {
			eid: pb.entityId(),
			x: pb.posX(),
			y: pb.posY(),
			s: { s: pb.sprite(), t: pb.spriteType() },
			a: 'APPEAR'
		};
		this._ctx.entityFactory.build(playerData, function (playerEntity) {
			// Follow the player
			LOG.info('Player build');
			this._ctx.playerEntity = playerEntity;
			// This is not clean as we must reference the internal sprite of the player.
			// Rework this when updating the render engine.
			this.game.camera.follow(playerEntity._sprite);
			this._ctx.pubsub.publish(Signal.ENGINE_GAME_STARTED);
		}.bind(this));

		LOG.info('Draw called');
		this._ctx.entityUpdater.releaseHold();
	}

	update() {

		// Calls the renderer.
		this._ctx.render.update();

		// Trigger the update effects.
		this._ctx.fxManager.update();

		this._ctx.indicatorManager.update();

		pathfinder.update();

		// Update the animation frame groups of all multi sprite entities.
		let entities = this._ctx.entityCache.getAllEntities();
		entities.forEach(function (entity) {
			entity.tickAnimation();
		});

		// Group sort the sprite layer.
		this._ctx.groups.sprites.sort('y', Phaser.Group.SORT_ASCENDING);
	}

	render() {
		// @ifdef DEVELOPMENT
		this.game.debug.cameraInfo(this.game.camera, 32, 32);
		// @endif
	}

	shutdown() {

	}

}