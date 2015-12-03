/**
 * Displays while the engine is loading files to display the next map. TODO Das
 * hier noch lokalisieren.
 * 
 * @constructor
 * @class Bestia.Engine.States.BootState
 */
Bestia.Engine.States.LoadingState = function() {

	this.bestia = null;
	this.text = 'Loading...';
	this.curProgress = 0;

};

Bestia.Engine.States.LoadingState.prototype.init = function(bestia) {
	this.bestia = bestia;

	console.debug("LoadState: Loading for bestia: " + this.bestia);

	// Prepare the loading screen.
	this.gfx = this.add.graphics(0, 0);
	this.gfx.beginFill(0xFF0000, 1);
};

Bestia.Engine.States.LoadingState.prototype.preload = function() {

	// Prepare to load the map specific stuff. This contains:
	// * Server suggested assets (mobs, entities, attacks, sounds)
	// * The mapfile itself (via asset pack)
	// * The player mob sprite.
	
	// Load the mapfile itself.
	var mapDbName = this.bestia.location();
	var packUrl = Bestia.Urls.assetsMap + mapDbName + '/assetpack.json';
	this.load.pack(mapDbName, packUrl);

	// Load the player bestia sprite.
	packUrl = Bestia.Urls.assetsMobSprite + this.bestia.sprite() + '_pack.json';
	this.load.pack(this.bestia.sprite(), packUrl);
	
	// Load all the server suggested files.
	// TODO

	this.load.onFileComplete.add(this.fileCompleted, this);
};

Bestia.Engine.States.LoadingState.prototype.loadUpdate = function() {

	this.game.debug.text(this.text, 10, 30, '#FFFFFF');
	var maxWidth = this.game.width - 20;
	this.gfx.drawRect(10, 60, (maxWidth * this.curProgress / 100), 20);

};

Bestia.Engine.States.LoadingState.prototype.create = function() {

	this.game.state.start('game', true, false, this.bestia);

};

Bestia.Engine.States.LoadingState.prototype.fileCompleted = function(progress) {

	this.curProgress = progress;
	this.text = "Complete: " + progress + "%";

};
