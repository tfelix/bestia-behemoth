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

	function preload() {
	}

	function create() {
	}

	function update() {

		// Update Ticker.
		info.fps(this.game.time.fps);
	}

	// Determine the size of the canvas.
	var height = $(document).height();
	var width = $('#canvas-container').width();

	var game = new Phaser.Game(width, height, Phaser.AUTO, 'bestia-canvas');

	game.state.add('game', new Bestia.Engine.States.GameState());
	game.state.add('loading', new Bestia.Engine.States.LoadingState());
	game.state.add('boot', new Bestia.Engine.States.BootState());

	game.state.start('boot');
};

Bestia.Engine.States = {};
