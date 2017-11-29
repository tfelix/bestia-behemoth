import groups, {GROUP_LAYERS} from '../Groups';
import PhaserDebug from '../plugins/phaser-debug';
import { engineContext, pathfinder } from '../EngineData';

/**
 * Central game state for controlling the games logic.
 * 
 * @constructor
 * @class Bestia.Engine.States.GameState
 * @param {Bestia.Engine}
 *            engine - Reference to the bestia engine.
 */
export default class GameState {

	constructor() {

		this._marker = null;
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
		engineContext.indicatorManager.create();

		// Activate move handler.
		engineContext.indicatorManager.showDefault();

		// ========= TESTING =========
		this.game.world.setBounds(0, 0, 800, 600);
		// ========= END TESTING =========
	}

	/**
	 * In this step the synchronize the bestia model with the phaser engine representation of sprites.
	 */
	update() {

		// Calls the renderer.
		engineContext.renderManager.update();
		engineContext.indicatorManager.update();

		pathfinder.update();

		// Group sort the sprite layer.
		groups.sort(GROUP_LAYERS.SPRITES);
	}

	render() {
		// @ifdef DEVELOPMENT
		this.game.debug.cameraInfo(this.game.camera, 32, 32);
		// @endif
	}
}