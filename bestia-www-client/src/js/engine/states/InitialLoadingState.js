/*global Phaser */

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
	
	constructor(context) {
		this._ctx = context;
		this._url = context.url;
		this._pubsub = context.pubsub;
	}

	/**
	 * Preload all basic assets which a normal game will need.
	 */
	preload() {
		// Initialize the context since our engine is now ready.
		this._ctx.init();
		
		let url = this._ctx.url;
		
		this.game.load.image('action_options_background', url.getImageUrl('action_options_back'));
		
		// TODO hier auch schon einen hinweis/splash anzeigen.
		this.game.load.spritesheet('tilesheet', 'http://localhost/assets/tileset/mountain_landscape_23.png', 32, 32);
		this.game.load.atlas('poring', 
				'http://localhost/assets/sprite/mob/poring/poring.png', 
				'http://localhost/assets/sprite/mob/poring/poring.json',
				Phaser.Loader.TEXTURE_ATLAS_JSON_HASH);
		
		
		this.game.load.image('castindicator_small', url.getIndicatorUrl('_big'));
		this.game.load.image('castindicator_medium', url.getIndicatorUrl('_medium'));
		this.game.load.image('castindicator_big', url.getIndicatorUrl('_small'));

		this.game.load.image('default_item', url.getItemIconUrl('_default'));

		// #### Filters
		this.game.load.script('filter_blur_x', url.getFilterUrl('BlurX'));
		this.game.load.script('filter_blur_y', url.getFilterUrl('BlurY'));

		this.game.load.spritesheet('rain', url.getSpriteUrl('rain'), 17, 17);

		// Load the static data from the manager.
		//this.ctx.indicatorManager.load();
		//this.ctx.fxManager.load();
	}

	create() {

		this._pubsub.publish(Signal.ENGINE_INIT_LOADED);
	}

}
