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
	
	this.options = {
		enableMusic : ko.observable('true'),
		musicVolume : ko.observable(100)
	};

	this.bestia = undefined;

	/**
	 * Context to hold very important and shared data between the states or
	 * other classes. Note that this object is only fully initialized after the
	 * engine has started (that means has passed the boot state).
	 */
	this.ctx = new Bestia.Engine.EngineContext(pubsub, this, urlHelper);

	// Determine the size of the canvas. And create the game object.
	var height = $(window).height();
	var width = $('#canvas-container').width();

	this.game = new Phaser.Game(width, height, Phaser.AUTO, 'bestia-canvas', null, false, false);

	this.game.state.add('boot', new Bestia.Engine.States.BootState(this));
	this.game.state.add('connecting', new Bestia.Engine.States.ConnectingState(this));
	this.game.state.add('initial_loading', new Bestia.Engine.States.InitialLoadingState(this));
	this.game.state.add('load', new Bestia.Engine.States.LoadingState(this));
	this.game.state.add('game', new Bestia.Engine.States.GameState(this));

	// ==== PREPARE HANDLER ====

	// React on bestia selection changes. We need to re-trigger the map loading.
	// This event will fire if we have established a connection.
	pubsub.subscribe(Bestia.Signal.BESTIA_SELECTED, this._onBestiaSelected.bind(this));
	pubsub.subscribe(Bestia.Signal.IO_CONNECTION_LOST, this._onConnectionLost.bind(this));
	pubsub.subscribe(Bestia.Signal.ENGINE_BOOTED, this._onBooted.bind(this));
	pubsub.subscribe(Bestia.Signal.ENGINE_INIT_LOADED, this._onInitLoaded.bind(this));
	pubsub.subscribe(Bestia.Signal.ENGINE_FINISHED_MAPLOAD, this._onFinishedMapload.bind(this));

	// When everything is setup. Start the engine.
	this.game.state.start('boot');
};

/**
 * Triggers a mapload if a bestia was selected.
 */
Bestia.Engine.prototype._onBestiaSelected = function(_, data) {
	console.debug('New bestia selected. Starting loading process.');
	this.bestia = data;
	this.loadMap(data);
};

/**
 * Shows the "now connecting" screen to visualize connection lost.
 */
Bestia.Engine.prototype._onConnectionLost = function() {
	this.game.state.start('connecting');
};

Bestia.Engine.prototype._onInitLoaded = function() {
	this.game.state.start('connecting');
};

Bestia.Engine.prototype._onBooted = function() {
	this.game.state.start('initial_loading');
};

Bestia.Engine.prototype._onFinishedMapload = function() {
	this.game.state.start('game', true, false, this.bestia);
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
	var world = this.ctx.zone;
	if (world === null || world.name !== bestia.location()) {
		// We need to do a full load.
		this.game.state.start('load', true, false, bestia);
	}
	// else: Partial load only (just switch view to active bestia).
	// TODO

};
