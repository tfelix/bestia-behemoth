import Signal from '../../io/Signal.js';

/**
 * The state is triggered for the first game loading. A real loading screen will
 * be shown but since we need to load more data then the normal ingame loading screen
 * we still need to load basic game assets like static engine sounds, image, logos etc.
 * 
 * @constructor
 * @class Bestia.Engine.States.InitialLoadingState
 */
export default class InitializeState {
	
	constructor(pubsub) {
		this._pubsub = pubsub;
	}

	/**
	 * Preload all basic assets which a normal game will need.
	 */
	preload() {
		// Initialize the context since our engine is now ready.
		
		let url = this._ctx.url;
		
		this.game.load.image('action_options_background', url.getImageUrl('action_options_back'));
		this.game.load.image('castindicator_small', url.getIndicatorUrl('_big'));
		this.game.load.image('castindicator_medium', url.getIndicatorUrl('_medium'));
		this.game.load.image('castindicator_big', url.getIndicatorUrl('_small'));
		this.game.load.image('default_item', url.getItemIconUrl('_default'));

		// Load the static data from the manager.
		//this._ctx.indicatorManager.load();
		//this._ctx.fxManager.load();
	}

	/**
	 * Signal the finished loading.
	 */
	create() {

		this._pubsub.publish(Signal.ENGINE_INIT_LOADED);
	}

}
