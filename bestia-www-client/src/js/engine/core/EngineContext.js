import EntityCacheManager from '../entities/util/EntityCacheManager.js';
import DemandLoader from '../core/DemandLoader.js';
import IndicatorManager from '../indicator/IndicatorManager.js';
import EffectsManager from '../fx/EffectsManager.js';
import EntityFactory from '../entities/EntityFactory.js';
import EntityUpdater from '../util/EntityUpdater.js';

/**
 * This class simply holds the reference to multiple important structures used
 * throughout the engine. Some of this structures might change during engine
 * operation but generally its a good idea to access all this objects through
 * this context object.
 * 
 * @author <thomas.felix@tfelix.de>
 */
export default class EngineContext {
	
	constructor(_pubsub, _engine, _urlHelper) {
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
		this._game = null;

		/**
		 * Bestia engine.
		 * 
		 * @property {Bestia.Engine}
		 */
		this.engine = _engine;

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
		 * Effects manager will subscribe itself to messages from the server which
		 * trigger a special effect for an entity or a stand alone effect which must
		 * be displayed by whatever means.
		 * 
		 * @public
		 * @property {Bestia.Engine.FX.EffectsManager}
		 */
		this.fxManager = null;

		this.groups = null;

		this.loader = null;

		this.indicatorManager = null;

		/**
		 * Bestia Zone Data.
		 */
		this.zone = null;

		/**
		 * Url Helper.
		 * 
		 * @property {Bestia.UrlHelper}
		 */
		this.url = _urlHelper;
	}

	/**
	 * Trigger all the stuff which can be done after an valie game object was
	 * set.
	 */
	_initGameSet() {

		// The order here is very important, since we set internal objects on which
		// some of the ctors of the objects depend. Please check twice when changing
		// this order if this will work!
		this.loader = new DemandLoader(this.game.load, this.game.cache, this.url);
		this.indicatorManager = new IndicatorManager(this);
		this.fxManager = new EffectsManager(this);
		this.entityFactory = new EntityFactory(this);
		this.entityUpdater = new EntityUpdater(this);
	}

	/**
	 * TODO Das zu einem getter setter machen.
	 */
	getPlayerEntity() {
		var pbid = this.engine.bestia.playerBestiaId();
		var entity = this.entityCache.getByPlayerBestiaId(pbid);
		return entity;
	}

	/**
	 * Phaser whipes the scene graph when states change. Thus one need to init
	 * the groups when the final (game_state) is started.
	 */
	createGroups() {

		if (!this.game) {
			console.warn("Game is not set. Can not create groups.");
			return;
		}

		// Groups can be created.
		this.groups = {};
		this.groups.mapGround = this.game.add.group();
		this.groups.mapGround.name = 'map_ground';
		this.groups.sprites = this.game.add.group();
		this.groups.sprites.name = 'sprites';
		this.groups.mapOverlay = this.game.add.group();
		this.groups.mapOverlay.name = 'map_overlay';
		this.groups.effects = this.game.add.group();
		this.groups.effects.name = 'fx';
		this.groups.overlay = this.game.add.group();
		this.groups.overlay.name = 'overlay';
		this.groups.gui = this.game.add.group();
		this.groups.gui.name = 'gui';
	}

	get game() {
		return this._game;
	}
	
	set game(value) {
			this._game = value;
			if (this._game) {
				this._initGameSet();
			}
	}
	
	/**
	 * Returns the currently selected player bestia.
	 */
	get playerBestia() {
		return this.engine.bestia;
	}
}


