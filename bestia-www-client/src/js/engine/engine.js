/**
 * Bestia Graphics engine. Responsible for displaying the game collecting user
 * input and sending these data to the server.
 * 
 * @class Bestia.Engine
 */
Bestia.Engine = function() {

	this.options = {
		enableMusic : ko.observable('true'),
		musicVolume : ko.observable(100)
	};

	this.info = {};
	this.info.fps = ko.observable(0);
	this.info.fps.extend({
		rateLimit : 1000
	});

	// Determine the size of the canvas.
	var height = $(document).height();
	var width = $('#canvas-container').width();

	var game = new Phaser.Game(width, height, Phaser.AUTO, 'bestia-canvas');

	game.state.add('game', new Bestia.Engine.States.GameState());
	game.state.add('loading', new Bestia.Engine.States.LoadingState());
	game.state.add('boot', new Bestia.Engine.States.BootState());

	game.state.start('loading');
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
 * Returns the tile coordinates when a pixel koordinate is given.
 * 
 * @static
 */
Bestia.Engine.px2cords = function(px) {
	return Math.floor(px / Bestia.Engine.Config.TILE_SIZE);
};

/**
 * Returns the px coordinates if a tile coordinate is given.
 * 
 * @static
 */
Bestia.Engine.cords2px = function(cords) {
	return cords * Bestia.Engine.Config.TILE_SIZE;
};