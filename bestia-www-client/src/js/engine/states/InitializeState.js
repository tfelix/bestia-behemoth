import Signal from '../../io/Signal.js';
import ReferenceName from '../ReferenceName';

/**
 * The state is triggered for the first game loading. A real loading screen will
 * be shown but since we need to load more data then the normal ingame loading
 * screen we still need to load basic game assets like static engine sounds,
 * image, logos etc.
 * 
 * @constructor
 * @class Bestia.Engine.States.InitialLoadingState
 */
export default class InitializeState {
	
	constructor(ctx) {
		
		this._ctx = ctx;
	}

	/**
	 * Preload all basic assets which a normal game will need.
	 */
	preload() {
		
		// Initialize the context since our engine is now ready.
		this.game.load.image('action_options_background', this._ctx.url.getImageUrl('action_options_back'));
		this.game.load.image('castindicator_small', this._ctx.url.getIndicatorUrl('_big'));
		this.game.load.image('castindicator_medium', this._ctx.url.getIndicatorUrl('_medium'));
		this.game.load.image('castindicator_big', this._ctx.url.getIndicatorUrl('_small'));
		this.game.load.image('default_item', this._ctx.url.getItemIconUrl('_default'));

		// Load the static data from the manager.
		this._ctx.indicatorManager.load();
		this._ctx.fxManager.load();
	}

	/**
	 * Signal the finished loading.
	 */
	create() {

		this._ctx.pubsub.publish(Signal.ENGINE_INIT_LOADED);
	}

}
