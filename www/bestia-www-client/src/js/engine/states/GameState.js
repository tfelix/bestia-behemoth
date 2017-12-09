import groups, {GROUP_LAYERS} from '../Groups';
import { engineContext, pathfinder } from '../EngineData';

/**
 * Central game state for controlling the games logic.
 * 
 * @constructor
 * @class GameState
 */
export default class GameState extends Phaser.Scene {

	constructor(config) {
		super(config);

		Phaser.Scene.call(this, {
			key: 'game'
		});

		this._marker = null;
	}

	create() {

		/**
		 * Phaser whipes the scene graph when states change. Thus one need to
		 * init the groups when the final (game_state) is started.
		 */
		// Groups can be created.
		groups.initilize(this);

		// ==== PLUGINS ====
		//this.game.plugins.add(PhaserDebug);
		this.game.stage.disableVisibilityChange = true;
		// ==== /PLUGINS ====

		// Trigger fx create effects.
		engineContext.indicatorManager.create();

		// Activate move handler.
		engineContext.indicatorManager.showDefault();
	}

	/**
	 * In this step the synchronize the bestia model with the phaser engine representation
	 * of sprites.
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
		this.debug.cameraInfo(this.game.camera, 32, 32);
	}
}