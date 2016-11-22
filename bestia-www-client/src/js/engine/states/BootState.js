
import Signal from '../../io/Signal.js';

/**
 * State is triggered once when the game starts. It will preload all the REALLY
 * needed assets in order to to a proper loading screen and do some basic (and
 * very important!) game setup. But other asset loadings should go into the
 * InitializeState which will show the user a proper loading screen.
 * 
 * @constructor
 */
export default class BootState {
	
	constructor(context) {
		this._ctx = context;
	}
	
	/**
	 * Preload all the needed assets in order to display a loading screen.
	 */
	preload() {
		let url = this._ctx.url;
		
		this.game.load.image('logo', url.getImageUrl('logo_small'));
	}
	
	create() {
		// Setup the game context.
		this._ctx.pubsub.publish(Signal.ENGINE_BOOTED);
	}
}