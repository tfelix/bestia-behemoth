import DemandLoader from '../core/DemandLoader.js';
import IndicatorManager from '../indicator/IndicatorManager.js';
import EffectsManager from '../fx/EffectsManager.js';
import EntityFactory from '../entities/factory/EntityFactory.js';
import EntityUpdater from '../entities/util/EntityUpdater.js';
import EntityCache from '../entities/util/EntityCache.js';
import TileRenderer from '../renderer/TileRenderer';
import RenderManager from '../renderer/RenderManager';

/**
 * This class simply holds the reference to multiple important structures used
 * throughout the engine. Some of this structures might change during engine
 * operation but generally its a good idea to access all this objects through
 * this context object.
 * 
 * @author <thomas.felix@tfelix.de>
 */
export default class EngineContext {
	
	constructor(game, _pubsub, _urlHelper) {
		/**
		 * Used to publish subscribe.
		 * 
		 * @property {Bestia.PubSub}
		 */
		this.pubsub = _pubsub;
		
		/**
		 * Phaser game reference.
		 * 
		 * @property {Phaser.Game}
		 * @private
		 */
		this.game = game;

		/**
		 * Factory for creating new entities in the running game. Usually this
		 * is used by the entity updater. Maybe public exposure is not needed.
		 */
		this.entityFactory = null;
		
		this.entityCache = new EntityCache();

		/**
		 * Entity updater for managing the adding and removal of entities.
		 * 
		 * @public
		 * @property {Bestia.Engine.EntityUpdater}
		 */
		this.entityUpdater = null;

		/**
		 * Effects manager will subscribe itself to messages from the server
		 * which trigger a special effect for an entity or a stand alone effect
		 * which must be displayed by whatever means.
		 * 
		 * @public
		 * @property {Bestia.Engine.FX.EffectsManager}
		 */
		this.fxManager = null;

		/**
		 * Holds a reference to the groups of the engine context.
		 * 
		 * @public
		 * @property {Bestia.Engine.FX.EffectsManager}
		 */
		this.groups = {};
		
		/**
		 * Multi purpose object to which one can attach objects in order to
		 * share them between different parts of the engine.
		 */
		this.etc = {};

		this.loader = null;

		this.indicatorManager = null;

		/**
		 * Url Helper.
		 * 
		 * @property {Bestia.UrlHelper}
		 */
		this.url = _urlHelper;
		
		/**
		 * Contains the player bestia view model.
		 */
		this.playerBestia = null;
		
		/**
		 * Contains the player bestia entity after it was created.
		 */
		this.playerEntity = null;
		
		this._hasInit = false;
	}
	
	/**
	 * When game object/state was removed clean should be called in order to
	 * clean up all old connection so the garbage collector can do its job and
	 * avoid memory leaks.
	 */
	clear() {
		
		if(!this._hasInit) {
			return;
		}
		
		//this.loader = null;
		//this.indicatorManager = null;
		//this.fxManager = null;
		//this.entityFactory = null;
		//this.entityUpdater = null;
		this.render.clear();
		this.playerEntity = null;
		
		// General utility objects.
		this.etc = {};
		this.groups = {};
	}
	
	/**
	 * Some initializations can only be done when a game state has been loaded.
	 */
	init() {
		this._hasInit = true;
		// The order here is very important, since we set internal objects on
		// which some of the ctors of the objects depend. Please check twice
		// when changing this order if this will work!
		this.loader = new DemandLoader(this.game.load, this.game.cache, this.url);
		this.indicatorManager = new IndicatorManager(this);
		this.fxManager = new EffectsManager(this);
		this.entityFactory = new EntityFactory(this);
		this.entityUpdater = new EntityUpdater(this);
		
		// Prepare the renderer
		let tileRenderer = new TileRenderer(this);
		
		/**
		 * The different renderer of the engine.
		 */
		this.render = new RenderManager();
		this.render.addRender(tileRenderer);
	}
}


