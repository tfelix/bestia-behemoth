/**
 * Bestia Graphics engine. Responsible for displaying the game collecting user
 * input and sending these data to the server.
 * 
 * @constructor
 * @class Bestia.Engine
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 * @param {Bestia.Config}
 *            config - Bestia Configuration object.
 */
Bestia.Engine = function(pubsub, config) {

	var self = this;

	/**
	 * @property {Bestia.Config} config - Holds a reference to the central
	 *           config object for the bestia game. So User options can be read
	 *           an used.
	 */
	this.config = config;

	/**
	 * @property {Bestia.PubSub} pubsub - Holds a reference to the bestia
	 *           publish/subscribe interface allowing game engine objects to
	 *           subscribe to events.
	 */
	this.pubsub = pubsub;

	this.options = {
		enableMusic : ko.observable('true'),
		musicVolume : ko.observable(100)
	};

	this.bestia = undefined;

	// Determine the size of the canvas.
	var height = $(window).height();
	var width = $('#canvas-container').width();
	this.game = new Phaser.Game(width, height, Phaser.AUTO, 'bestia-canvas', null, false, false);

	this.gameState = new Bestia.Engine.States.GameState(this);
	this.game.state.add('boot', new Bestia.Engine.States.BootState());
	this.game.state.add('connecting', new Bestia.Engine.States.ConnectingState(this));
	this.game.state.add('load', new Bestia.Engine.States.LoadingState(this));
	this.game.state.add('game', this.gameState);

	/**
	 * Holds the central cache for all entities displayed in the game.
	 */
	this.entityCache = new Bestia.Engine.EntityCacheManager();

	/**
	 * Entity updater for managing the adding and removal of entities.
	 * 
	 * @public
	 * @property {Bestia.Engine.EntityUpdater}
	 */
	this.entityUpdater = new Bestia.Engine.EntityUpdater(pubsub, this.entityCache);

	// React on bestia selection changes. We need to re-trigger the map loading.
	var onSelectBestiaHandler = function(_, data) {
		console.debug('New bestia selected. Starting loading process.');
		self.bestia = data;
		self.loadMap(data);
	};
	pubsub.subscribe(Bestia.Signal.BESTIA_SELECTED, onSelectBestiaHandler);

	// When everything is setup. Start the engine.
	this.game.state.start('boot');
};

/**
 * Loads a certain map. If the map is different then the current map it will
 * trigger a complete map reload. Otherwise it will just do a partial load an
 * shift the active viewport to the newly selected bestia.
 * 
 * @param {Bestia.BestiaViewModel}
 *            bestia - Bestia to use as the player character.
 * @method Bestia.Engine#loadMap
 */
Bestia.Engine.prototype.loadMap = function(bestia) {
	console.debug('Loading map.');

	// See if we can do a partial mapload or a full map reload.
	var world = this.gameState.bestiaWorld;
	if (world === null || world.name !== bestia.location()) {
		// We need to do a full load.
		this.game.state.start('load', true, false, bestia);
	}
	// else: Partial load only (just switch view to active bestia).
	// TODO

};

/**
 * Static holder for the different engine states. will be added in the ctor of
 * the engine to the phaser.js system.
 */
Bestia.Engine.States = {};