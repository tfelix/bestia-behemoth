/**
 * Displays while the engine is loading files to display the next map. TODO Das
 * hier noch lokalisieren.
 * 
 * @constructor
 * @class Bestia.Engine.States.BootState
 */
Bestia.Engine.States.LoadingState = function() {

	this.bestia = null;

};

Bestia.Engine.States.LoadingState.prototype = {

	init : function(bestia) {
		this.bestia = bestia;
	},

	create : function() {	
		console.debug("LoadState: Loading for bestia: " + this.bestia);		
		
		var mapDbName = this.bestia.location();
		
		this.gfx = this.add.graphics(0, 0);
		this.gfx.beginFill(0xFF0000, 1);

		this.text = 'Loading...';
		this.curProgress = 0;

		// You can listen for each of these events from Phaser.Loader
		this.load.onFileComplete.add(this.fileComplete, this);
		this.load.onLoadComplete.add(this.loadComplete, this);
		
		var packUrl = Bestia.Urls.assetsMap + mapDbName +'/assetpack.json';
		this.load.pack(mapDbName, packUrl);
		
		// Load the bestia sprite.
		packUrl = Bestia.Urls.assetsMobSprite + this.bestia.sprite() + '_pack.json';
		this.load.pack(this.bestia.sprite(), packUrl);
		
		this.load.start();
	},

	render : function() {
		this.game.debug.text(this.text, 10, 30, '#FFFFFF');
		var maxWidth = this.game.width - 20;
		this.gfx.drawRect(10, 60, (maxWidth * this.curProgress / 100), 20);

	},

	fileComplete : function(progress, cacheKey, success, totalLoaded, totalFiles) {
		this.curProgress = progress;
		this.text = "File Complete: " + progress + "% - " + totalLoaded + " out of " + totalFiles;
	},

	loadComplete : function() {
		this.text = "Load completed.";

		this.load.onFileComplete.removeAll();
		this.load.onLoadComplete.removeAll();

		this.game.state.start('game', true, false, this.bestia);
	}
};

Bestia.Engine.States.LoadingState.prototype.constructor = Bestia.Engine.States.LoadingState;