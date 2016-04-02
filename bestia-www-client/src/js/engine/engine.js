/**
 * Bestia Graphics engine. Responsible for displaying the game collecting user
 * input and sending these data to the server.
 * 
 * @constructor
 * @class Bestia.Engine
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 */
Bestia.Engine = function(pubsub, urlHelper) {

	var self = this;

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

	this.urlHelper = urlHelper;

	// Determine the size of the canvas. And create the game object.
	var height = $(window).height();
	var width = $('#canvas-container').width();
	
	this.game = new Phaser.Game(width, height, Phaser.AUTO, 'bestia-canvas', null, false, false);

	this.gameState = new Bestia.Engine.States.GameState(this, this.urlHelper);
	this.game.state.add('boot', new Bestia.Engine.States.BootState());
	this.game.state.add('connecting', new Bestia.Engine.States.ConnectingState(pubsub));
	this.game.state.add('init_load', new Bestia.Engine.States.InitialLoadingState(urlHelper));
	this.game.state.add('load', new Bestia.Engine.States.LoadingState(this, urlHelper));
	this.game.state.add('game', this.gameState);

	// ==== PREPARE HANDLER ====

	// React on bestia selection changes. We need to re-trigger the map loading.
	// This event will fire if we have established a connection.
	pubsub.subscribe(Bestia.Signal.BESTIA_SELECTED, function(_, data) {
		console.debug('New bestia selected. Starting loading process.');
		self.bestia = data;
		self.loadMap(data);
	});

	/**
	 * Bring the engine in the connecting screen.
	 */
	pubsub.subscribe(Bestia.Signal.IO_CONNECTION_LOST, function() {
		self.game.state.start('connecting');
	});

	// Switch the state to the game state.
	pubsub.subscribe(Bestia.Signal.ENGINE_FINISHED_MAPLOAD, function() {
		self.game.state.start('game', true, false, self.bestia);
	});

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

	// Check if we can do a partial mapload or a full map reload.
	var world = this.gameState.bestiaWorld;
	if (world === null || world.name !== bestia.location()) {
		// We need to do a full load.
		this.game.state.start('load', true, false, bestia);
	}
	// else: Partial load only (just switch view to active bestia).
	// TODO

};

