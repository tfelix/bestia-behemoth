
import Signal from '../../io/Signal.js';
import UrlHelper from '../../util/UrlHelper';
import ReferenceName from '../ReferenceName';
import IndicatorManager from '../indicator/IndicatorManager';
import EffectsManager from '../fx/EffectsManager';
import EntityFactory from '../entities/factory/EntityFactory';
import RenderManager from '../renderer/RenderManager';
import EngineMediator from '../EngineMediator';
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
	
	constructor(gamePubSub, pubsub, url) {
		this._gamePubSub = gamePubSub;
		
		/**
		 * Will be extended later.
		 */
		this._url = null;
		
		// Create all other components.
		new EntityCache(gamePubSub);
		new EngineMediator(gamePubSub, pubsub);
		gamePubSub.setRef(ReferenceName.UrlHelper, url);
	}
	
	/**
	 * Preload all the needed assets in order to display a loading screen.
	 */
	preload() {
		this._gamePubSub.setRef(ReferenceName.PhaserGame, this.game);
		this._gamePubSub.setRef(ReferenceName.PhaserLoader, this.game.load);
		this._gamePubSub.setRef(ReferenceName.PhaserCache, this.game.cache);
		
		// Extend this with the url helper.
		this._gamePubSub.extendRef({ref: ReferenceName.UrlHelper, member: '_url'}, this);
		
		this.game.load.image('logo', this._url.getImageUrl('logo_small'));
		
		// Setup the parts of the engine which depend upon phaser parts.
		new DemandLoader(this._gamePubSub);
		new IndicatorManager(this._gamePubSub);
		new EffectsManager(this._gamePubSub);
		new RenderManager(this._gamePubSub);
		new EntityFactory(this._gamePubSub);
	}
	
	create() {
		// Setup the game context.
		this._gamePubSub.publish(Signal.ENGINE_BOOTED);
	}
}