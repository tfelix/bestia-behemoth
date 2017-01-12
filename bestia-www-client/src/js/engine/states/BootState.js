
import Signal from '../../io/Signal.js';

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
	
	constructor(pubsub) {
		this._pubsub = pubsub;
	}
	
	/**
	 * Preload all the needed assets in order to display a loading screen.
	 */
	preload() {
		this.game.load.NAME = 'phaserLoader';
		this._pubsub(Signal.ENGINE_SETREF, this.game.load);
		
		let url = this._ctx.url;
		
		this.game.load.image('logo', url.getImageUrl('logo_small'));
	}
	
	create() {
		// Setup the game context.
		this._ctx.pubsub.publish(Signal.ENGINE_BOOTED);
	}
}