/**
 * Bestia Graphics engine. Responsible for displaying the game collecting user
 * input and sending these data to the server.
 * 
 * @constructor
 * @class Bestia.Engine
 */
Bestia.Engine = function(config) {

	var self = this;

	/**
	 * @property {Bestia.Config} config - Holds a reference to the central
	 *           config object for the bestia game. So User options can be read
	 *           an used.
	 */
	this.config = config;

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

	// Determine the size of the canvas.
	var height = $(document).height();
	var width = $('#canvas-container').width();

	this.game = new Phaser.Game(width, height, Phaser.AUTO, 'bestia-canvas');

	this.game.state.add('game', new Bestia.Engine.States.GameState(this));
	this.game.state.add('load', new Bestia.Engine.States.LoadingState(this));
	this.game.state.add('boot', new Bestia.Engine.States.BootState(this));
	this.game.state.start('boot');

	// Subscribe for the first info messages until we gathered information about
	// the master bestia to trigger initial map load.
	var onSelectBestiaHandler = function(_, data) {
		console.debug('New bestia selected. Starting loading process.');
		self.loadMap(data);
	};

	var onInitHandler = function(_, data) {
		console.debug('Engine.onInitHandler called. Starting initial load and remove handler.');
		var bestia = new Bestia.BestiaViewModel(null, data.bm);
		self.loadMap(bestia);
		Bestia.unsubscribe('bestia.info', onInitHandler);
	};

	Bestia.subscribe('bestia.info', onInitHandler);
	Bestia.subscribe('engine.selectBestia', onSelectBestiaHandler);
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
	if (undefined === this.bestia || this.bestia.location() !== bestia.location()) {
		// We need to do a full load.
		this.game.state.start('load', true, false, bestia);
	}
	// Partial load (switch view to active bestia).
	// TODO

};

/**
 * Static holder for the different engine states. will be added in the ctor of
 * the engine to the phaser.js system.
 */
Bestia.Engine.States = {};

/**
 * Holds static and constant configuration data for the bestia engine.
 * 
 * @constant
 */
Bestia.Engine.Config = {
	TILE_SIZE : 32
};

/**
 * Returns the tile coordinates when a pixel koordinate is given. TODO Phaser
 * kann das wohl auch. Entfernen.
 * 
 * @static
 */
Bestia.Engine.px2cords = function(px) {
	return Math.floor(px / Bestia.Engine.Config.TILE_SIZE);
};

/**
 * Returns the px coordinates if a tile coordinate is given. TODO Phaser kann
 * das wohl auch. Entfernen.
 * 
 * @static
 */
Bestia.Engine.cords2px = function(cords) {
	return cords * Bestia.Engine.Config.TILE_SIZE;
};