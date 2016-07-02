
import Signal from '../../io/Signal.js';

/**
 * State is triggered once when the game starts. It will preload all the really
 * needed assets in order to to a proper loading screen and do some basic (and
 * very important!) game setup.
 * 
 * @constructor
 * @class Bestia.Engine.States.BootState
 */
export default class BootState {
	
	constructor(engine) {
		this._engine = engine;
	}
	
	/**
	 * Preload all the needed assets in order to display a loading screen.
	 */
	preload() {
		// TBD
	}
	
	create() {
		// Setup the game context.
		var ctx = this._engine.ctx;
		
		ctx.game = this.game;

		ctx.pubsub.publish(Signal.ENGINE_BOOTED);
	}
}