/**
 * Displays while the engine is loading files to display the next map.
 * 
 * @constructor
 * @class Bestia.Engine.States.BootState
 */
Bestia.Engine.States.LoadingState = function() {
	this.text = 'Loading...';
};

Bestia.Engine.States.LoadingState.prototype = {
	preload : function() {

	},

	create : function() {
		this.gfx = this.add.graphics(0, 0);
		this.gfx.beginFill(0xFF0000, 1);

		// You can listen for each of these events from Phaser.Loader
		//this.game.load.onLoadStart.add(this.loadStart, this);
		this.load.onFileComplete.addOnce(this.fileComplete, this);
		this.load.onLoadComplete.addOnce(this.loadComplete, this);
		
		// TEMP
		this.load.image('logo', 'assets/img/logo_small.png');
		this.load.tilemap('map', 'assets/map/test-zone1/test-zone1.json', null, Phaser.Tilemap.TILED_JSON);
		this.load.image('tiles', 'assets/map/test-zone1/tilemap1.png');

		// Sprites.
		this.load.image('1_F_ORIENT_01', 'assets/sprite/1_F_ORIENT_01.png');
		this.load.image('1_M_BARD', 'assets/sprite/1_M_BARD.png');

		this.load.audio('bg_theme', 'assets/sound/theme/prontera_fields.mp3');

		// ATLAS
		this.load.atlasJSONHash('poring', 'assets/sprite/mob/poring.png', 'assets/sprite/mob/poring.json');
		
		this.load.start();
		
	},
	
	render : function() {
		this.game.debug.text(this.text, 10, 30, '#FFFFFF');
	},
	
	/**
	 * Starts the loading of the given files. Progress will be displayed.
	 * 
	 * @method Bestia.Engine.States.LoadingState#loadAssets
	 */
	loadAssets : function() {
		
	},
	
	fileComplete : function(progress, cacheKey, success, totalLoaded, totalFiles) {
		this.text = "File Complete: " + progress + "% - " + totalLoaded + " out of " + totalFiles;
		//this.game.debug.text(text, 10, 30, '#FFFFFF');
	},
	
	loadComplete : function() {
		var self = this;
		this.text = "Load completed.";
		window.setTimeout(function(){
			self.game.state.start('game');
		}, 500);
	}
};

Bestia.Engine.States.LoadingState.prototype.constructor = Bestia.Engine.States.LoadingState;