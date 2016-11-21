/*global Phaser */

import Signal from '../../io/Signal.js';

/**
 * The state is triggered for the first game loading. A real loading screen will
 * be shown but since we need to load more data then the normal loading screen
 * we still need to load basic game assets. Because of the really bootstrapping
 * boot state we are able to display a loading indicator screen.
 * 
 * @constructor
 * @class Bestia.Engine.States.InitialLoadingState
 */
export default class InitializeState {
	
	constructor(context) {
		this.ctx = context;
		this.url = context.url;
		this._pubsub = context.pubsub;
	}

	preload() {
		
		// TODO hier auch schon einen hinweis/splash anzeigen.
		this.game.load.spritesheet('tilesheet', 'http://localhost/assets/tileset/mountain_landscape_23.png', 32, 32);
		this.game.load.atlas('poring', 
				'http://localhost/assets/sprite/mob/poring/poring.png', 
				'http://localhost/assets/sprite/mob/poring/poring.json',
				Phaser.Loader.TEXTURE_ATLAS_JSON_HASH);
		
		
		this.game.load.image('castindicator_small', this.url.getIndicatorUrl('_big'));
		this.game.load.image('castindicator_medium', this.url.getIndicatorUrl('_medium'));
		this.game.load.image('castindicator_big', this.url.getIndicatorUrl('_small'));

		this.game.load.image('default_item', this.url.getItemIconUrl('_default'));

		// #### Filters
		this.game.load.script('filter_blur_x', this.url.getFilterUrl('BlurX'));
		this.game.load.script('filter_blur_y', this.url.getFilterUrl('BlurY'));

		this.game.load.spritesheet('rain', this.url.getSpriteUrl('rain'), 17, 17);

		// Load the static data from the manager.
		//this.ctx.indicatorManager.load();
		//this.ctx.fxManager.load();
	}

	create() {

		this._pubsub.publish(Signal.ENGINE_INIT_LOADED);
	}

}
