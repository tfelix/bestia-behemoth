import EntityCacheManager from '../entities/util/EntityCacheManager.js';
import DemandLoader from '../core/DemandLoader.js';
import IndicatorManager from '../indicator/IndicatorManager.js';
import EffectsManager from '../fx/EffectsManager.js';
import EntityFactory from '../entities/factory/EntityFactory.js';
import EntityUpdater from '../entities/util/EntityUpdater.js';
import TileRenderer from '../renderer/TileRenderer';

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

		this.entityCache = new EntityCacheManager();

		this.entityFactory = null;

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

		this.loader = null;

		this.indicatorManager = null;

		/**
		 * Url Helper.
		 * 
		 * @property {Bestia.UrlHelper}
		 */
		this.url = _urlHelper;
		
		this.playerBestia = null;
	}
	
	/**
	 * When game object/state was removed clean should be called in order to
	 * clean up all old connection so the garbage colletor can do its job and
	 * acoid memory leaks.
	 */
	clear() {
		this.loader = null;
		this.indicatorManager = null;
		this.fxManager = null;
		this.entityFactory = null;
		this.entityUpdater = null;
		
		// Prepare the renderer
		this.renderer = {};
		this.groups = {};
	}
	
	/**
	 * Some initializations can only be done when a game state has been loaded.
	 */
	init() {
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
		this.renderer = {
				tile: tileRenderer
		};
	}

	/**
	 * Returns the entity wrapper object of the player bestia.
	 */
	get playerEntity() {
		var pbid = this.engine.bestia.playerBestiaId();
		var entity = this.entityCache.getByPlayerBestiaId(pbid);
		return entity;
	}
}


