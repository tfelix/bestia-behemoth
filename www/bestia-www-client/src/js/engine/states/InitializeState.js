import * as Phaser from 'phaser';
import Signal from '../../io/Signal.js';
import LOG from '../../util/Log';
import RenderManager from '../renderer/RenderManager';
import TileRenderer from '../renderer/TileRenderer';
import EntityRenderer from '../renderer/EntityRenderer';
import { DebugRenderer } from '../renderer/DebugRenderer';
import { EntityMenuRenderer } from '../renderer/EntityMenuRenderer';
import IndicatorManager from '../indicator/IndicatorManager';
import DemandLoader from '../DemandLoader';
import { engineContext } from '../EngineData';


/**
 * The state is triggered for the first game loading. A real loading screen will
 * be shown but since we need to load more data then the normal ingame loading
 * screen we still need to load basic game assets like static engine sounds,
 * image, logos etc.
 * 
 * @constructor
 * @class InitializeState
 */
export default class InitializeState extends Phaser.Scene {

	constructor(config) {
		super(config);

		Phaser.Scene.call(this, {
			key: 'initialize'
		});
	}

	/**
	 * Preload all basic assets which a normal game will need.
	 */
	preload() {

		// Perform the outstanding inits.
		// Since the objects often reference to the engine context inside their 
		// ctor the order of the initialization is really important. Nether the less accessing the
		// methods of the engine ctx inside the object ctor should be avoided to tackle the problem.		
		engineContext.loader = new DemandLoader(this);
		engineContext.indicatorManager = new IndicatorManager();

		// ==== PREPARE RENDERER ====
		engineContext.renderManager = new RenderManager();
		engineContext.renderManager.addRender(new TileRenderer(engineContext.pubsub, this));
		engineContext.renderManager.addRender(new EntityRenderer(engineContext.pubsub, this));
		engineContext.renderManager.addRender(new DebugRenderer(this));
		engineContext.renderManager.addRender(new EntityMenuRenderer(this));

		// Load all static render assets.
		engineContext.renderManager.load(this.load);

		// Load the static data from the manager.
		engineContext.indicatorManager.load(this.load);
	}

	/**
	 * Signal the finished loading.
	 */
	create() {
		LOG.info('Initializing finished. switching to: connecting state.');
		engineContext.pubsub.publish(Signal.ENGINE_INIT_LOADED);
		engineContext.pubsub.publish(Signal.IO_CONNECT);
		this.scene.start('connect');
	}
}
