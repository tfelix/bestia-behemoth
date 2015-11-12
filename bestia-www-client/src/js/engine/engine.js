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

	this.info = {};
	this.info.fps = ko.observable(0);
	this.info.fps.extend({
		rateLimit : 1000
	});

	this.bestia = undefined;
	
	this.world = undefined;

	// Determine the size of the canvas.
	var height = $(window).height();
	var width = $('#canvas-container').width();
	this.game = new Phaser.Game(width, height, Phaser.AUTO, 'bestia-canvas');

	this.game.state.add('game', new Bestia.Engine.States.GameState(this));
	this.game.state.add('connecting', new Bestia.Engine.States.ConnectingState(this));
	this.game.state.add('load', new Bestia.Engine.States.LoadingState(this));
	this.game.state.start('connecting');

	this.entityCache = new Bestia.Engine.EntityCacheManager();
	
	/**
	 * Entity updater for managing the adding and removal of entities.
	 * 
	 * @public
	 * @property {Bestia.Engine.EntityUpdater}
	 */
	this.entityUpdater = new Bestia.Engine.EntityUpdater(pubsub, this.entityCache);

	// Subscribe for the first info messages until we gathered information about
	// the master bestia to trigger initial map load.
	var onInitHandler = function(_, bestia) {
		console.debug('Engine.onInitHandler called. Starting initial load and remove handler.');
		self.loadMap(bestia);
		// Remove handler again since we only trigger this once.
		self.pubsub.unsubscribe('client.selectedBestia', onInitHandler);
	};
	pubsub.subscribe('client.selectedBestia', onInitHandler);

	// React on bestia selection changes. We need to re-trigger the map loading.
	var onSelectBestiaHandler = function(_, data) {
		console.debug('New bestia selected. Starting loading process.');
		self.loadMap(data);
	};
	pubsub.subscribe('client.selectedBestia', onSelectBestiaHandler);
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
	if (this.world === undefined || this.world.name !== bestia.location()) {
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