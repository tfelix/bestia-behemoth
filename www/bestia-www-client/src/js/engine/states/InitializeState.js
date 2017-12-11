import * as Phaser from 'phaser';
import Signal from '../../io/Signal.js';
import LOG from '../../util/Log';
import RenderManager from '../renderer/RenderManager';
import TileRenderer from '../renderer/TileRenderer';
import EntityRenderer from '../renderer/EntityRenderer';
import { DebugRenderer } from '../renderer/DebugRenderer';
import IndicatorManager from '../indicator/IndicatorManager';
import DemandLoader from '../DemandLoader';


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

	constructor(context) {
		super();

		Phaser.Scene.call(this, {
			key: 'initialize'
		});

		this._context = context;
	}

	/**
	 * Preload all basic assets which a normal game will need.
	 */
	preload() {
		let logo = this.add.image(0, 0, 'bestia-logo');
		logo.setOrigin(0.5);

		// Perform the outstanding inits.
		// Since the objects often reference to the engine context inside their 
		// ctor the order of the initialization is really important. Nether the less accessing the
		// methods of the engine ctx inside the object ctor should be avoided to tackle the problem.		
		this._context.loader = new DemandLoader(this);
		this._context.indicatorManager = new IndicatorManager(this._context);

		// ==== PREPARE RENDERER ====
		this._context.renderManager = new RenderManager();
		this._context.renderManager.addRender(new TileRenderer(this._context));
		this._context.renderManager.addRender(new EntityRenderer(this._context));
		this._context.renderManager.addRender(new DebugRenderer(this._context));

		// Load all static render assets.
		this._context.renderManager.load(this.load);

		// Load the static data from the manager.
		this._context.indicatorManager.load(this.load);
	}

	/**
	 * Signal the finished loading.
	 */
	create() {
		LOG.info('Initializing finished. switching to: connecting state.');

		// Prevent rightclick on canvas.
		this.game.canvas.oncontextmenu = (e) => e.preventDefault();

		this._context.pubsub.publish(Signal.ENGINE_INIT_LOADED);
		this._context.pubsub.publish(Signal.IO_CONNECT);
		this.scene.start('connect');
	}
}
