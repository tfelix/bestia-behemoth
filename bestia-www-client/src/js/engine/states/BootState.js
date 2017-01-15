
import Signal from '../../io/Signal.js';
import UrlHelper from '../../util/UrlHelper';
import IndicatorManager from '../indicator/IndicatorManager';
import EffectsManager from '../fx/EffectsManager';
import EntityFactory from '../entities/factory/EntityFactory';
import RenderManager from '../renderer/RenderManager';
import DemandLoader from '../core/DemandLoader';
import EntityCache from '../entities/util/EntityCache';

/**
 * State is triggered once when the game starts. It will preload all the REALLY
 * important and needed assets in order to to a proper loading screen and do
 * some basic (but very important!) game setup. Other asset loadings should go
 * into the InitializeState which will then show the user a proper loading
 * screen.
 * 
 * @constructor
 */
export default class BootState {
	
	constructor(ctx) {
		
		this._ctx = ctx;
	}
	
	/**
	 * Preload all the needed assets in order to display a loading screen.
	 */
	preload() {
		
		// Initialize the context with the new created phaser objects.
		this._ctx.initialize(this.game);
		
		this.game.load.image('logo', this._ctx.url.getImageUrl('logo_small'));
	}
	
	create() {
		// Setup the game context.
		this._ctx.pubsub.publish(Signal.ENGINE_BOOTED);
	}
}