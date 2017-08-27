/*global Phaser */

import Signal from '../../io/Signal.js';
import groups, {GROUP_LAYERS} from '../core/Groups';
import TileRender from '../renderer/TileRenderer';
import renderManager from '../renderer/RenderManager';
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
		groups.initilize(this.game);

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

		this._ctx.entityUpdater.releaseHold();
	}

	/**
	 * In this step the synchronize the bestia model with the phaser engine representation of sprites.
	 */
	update() {

		// Calls the renderer.
		renderManager.update();

		// Trigger the update effects.
		this._ctx.fxManager.update();

		this._ctx.indicatorManager.update();

		//pathfinder.update();

		// Update the animation frame groups of all multi sprite entities.
		let entities = this._ctx.entityCache.getAllEntities();
		entities.forEach(function (entity) {
			entity.tickAnimation();
		});

		// Group sort the sprite layer.
		groups.sort(GROUP_LAYERS.SPRITES);
	}

	render() {
		// @ifdef DEVELOPMENT
		this.game.debug.cameraInfo(this.game.camera, 32, 32);
		// @endif
	}

	shutdown() {

	}

}