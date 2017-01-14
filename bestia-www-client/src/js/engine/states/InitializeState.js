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
	
	constructor(pubsub) {
		this._pubsub = pubsub;
		
		this._url = null;
	}

	/**
	 * Preload all basic assets which a normal game will need.
	 */
	preload() {
		// Initialize the context since our engine is now ready.
		
		this._pubsub.extendRef([
			{ref: ReferenceName.UrlHelper, member: '_url'},
			{ref: ReferenceName.IndicatorManager, member: '_indicatorManager'},
			{ref: ReferenceName.EffectsManager, member: '_fxManager'}], this);
		
		this.game.load.image('action_options_background', this._url.getImageUrl('action_options_back'));
		this.game.load.image('castindicator_small', this._url.getIndicatorUrl('_big'));
		this.game.load.image('castindicator_medium', this._url.getIndicatorUrl('_medium'));
		this.game.load.image('castindicator_big', this._url.getIndicatorUrl('_small'));
		this.game.load.image('default_item', this._url.getItemIconUrl('_default'));

		// Load the static data from the manager.
		this._indicatorManager.load();
		this._fxManager.load();
	}

	/**
	 * Signal the finished loading.
	 */
	create() {

		this._pubsub.publish(Signal.ENGINE_INIT_LOADED);
	}

}
