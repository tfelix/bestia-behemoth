
import Signal from '../../io/Signal.js';
import UrlHelper from '../../util/UrlHelper';
import ReferenceName from '../ReferenceName';

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
		
		/**
		 * Will be extended later.
		 */
		this._url = null;
	}
	
	/**
	 * Preload all the needed assets in order to display a loading screen.
	 */
	preload() {
		this._pubsub.setRef(ReferenceName.PhaserLoader, this.game.load);
		
		// Extend this with the url helper.
		this._pubsub.extendRefs({ref: ReferenceName.UrlHelper, member: '_url'}, this);
		
		this.game.load.image('logo', this._url.getImageUrl('logo_small'));
	}
	
	create() {
		// Setup the game context.
		this._pubsub.publish(Signal.ENGINE_BOOTED);
	}
}