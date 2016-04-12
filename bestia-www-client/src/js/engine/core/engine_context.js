/**
 * This class simply holds the reference to multiple important structures used
 * throughout the engine. Some of this structures might change during engine
 * operation but generally its a good idea to access all this objects throught
 * this context object.
 * 
 * @author <thomas.felix@tfelix.de>
 */
Bestia.Engine.EngineContext = function(_pubsub, _engine, _urlHelper) {

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

	this.entityCache = new Bestia.Engine.EntityCacheManager();

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

};

/**
 * Trigger all the stuff which can be done after an valie game object was set.
 */
Bestia.Engine.EngineContext.prototype._initGameSet = function() {

	// The order here is very important, since we set internal objects on which
	// some of the ctors of the objects depend. Please check twice when changing
	// this order if this will work!
	this.loader = new Bestia.Engine.DemandLoader(this.game.load, this.game.cache, this.url);
	this.indicatorManager = new Bestia.Engine.IndicatorManager(this);
	this.fxManager = new Bestia.Engine.FX.EffectsManager(this);
	this.entityFactory = new Bestia.Engine.EntityFactory(this);
	this.entityUpdater = new Bestia.Engine.EntityUpdater(this);
};

Bestia.Engine.EngineContext.prototype.getPlayerEntity = function() {
	var pbid = this.engine.bestia.playerBestiaId();
	var entity = this.entityCache.getByPlayerBestiaId(pbid);
	return entity;
};

/**
 * Phaser whipes the scene graph when states change. Thus one need to init the
 * groups when the final (game_state) is started.
 */
Bestia.Engine.EngineContext.prototype.createGroups = function() {

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
};

Object.defineProperty(Bestia.Engine.EngineContext.prototype, 'game', {
	get : function() {
		return this._game;
	},
	set : function(value) {
		this._game = value;
		if (this._game) {
			this._initGameSet();
		}
	}
});