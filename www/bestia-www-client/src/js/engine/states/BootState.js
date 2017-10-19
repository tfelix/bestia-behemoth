import Signal from '../../io/Signal.js';
import LOG from '../../util/Log';
import {engineContext} from '../EngineData';

var style = {
	font : 'bold 32px Arial',
	fill : '#fff',
	boundsAlignH :'center',
	boundsAlignV : 'middle'
};

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
	
	constructor() {
		//no op
	}
	
	/**
	 * Preload all the needed assets in order to display a loading screen.
	 */
	preload() {		
		// TODO Load all needed data.
	}
	
	create() {
		var txt = this.game.add.text(this.game.world.centerX, this.game.world.centerY, 'Booting', style);
		
		// Prevent rightclick.
		this.game.canvas.oncontextmenu = (e) => e.preventDefault();
		
		// Setup the game context.
		engineContext.pubsub.publish(Signal.ENGINE_BOOTED);

		LOG.info('Booting finished. Starting to initialize.');
		this.game.state.start('initial_loading');
	}
}