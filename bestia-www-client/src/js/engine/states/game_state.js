Bestia.Engine.States = Bestia.Engine.States || {};

/**
 * Central game state for controlling the games logic.
 * 
 * @constructor
 * @class Bestia.Engine.States.GameState
 * @param {Bestia.Engine}
 *            engine - Reference to the bestia engine.
 */
Bestia.Engine.States.GameState = function(engine) {

	this.marker = null;

	this.ctx = engine.ctx;

};

Bestia.Engine.States.GameState.prototype.create = function() {
	
	// ==== PLUGINS ====
	var astar = this.game.plugins.add(Phaser.Plugin.AStar);

	// @ifdef DEVELOPMENT
	this.game.plugins.add(Phaser.Plugin.Debug);
	// @endif
	// ==== /PLUGINS ====
	
	this.ctx.createGroups();
	
	// Trigger fx create effects.
	this.ctx.fxManager.create();
	this.ctx.indicatorManager.create();

	// Load the tilemap and display it.
	this.ctx.zone = new Bestia.Engine.World(this.game, astar, this.ctx.groups);
	this.ctx.zone.loadMap(this.ctx.playerBestia.location());

	// @ifdef DEVELOPMENT
	this.game.stage.disableVisibilityChange = true;
	// @endif

	this.ctx.pubsub.publish(Bestia.Signal.ENGINE_GAME_STARTED);
	this.ctx.entityUpdater.releaseHold();	
	
	// Activate move handler.
	this.ctx.indicatorManager.showDefault();
};

Bestia.Engine.States.GameState.prototype.update = function() {
	
	// Trigger the update effects.
	this.ctx.fxManager.update();

	// Update the animation frame groups of all multi sprite entities.
	var entities = this.ctx.entityCache.getAllEntities();
	entities.forEach(function(entity) {
		entity.tickAnimation();
	});
	
	// Group sort the sprite layer.
	this.ctx.groups.sprites.sort('y', Phaser.Group.SORT_ASCENDING);

};

Bestia.Engine.States.GameState.prototype.shutdown = function() {

	// We need to UNSUBSCRIBE from all subscriptions to avoid leakage.
	// TODO Ich weiß nicht ob das hier funktioniert oder ob referenz zu callback
	// benötigt wird.
	//this.pubsub.unsubscribe(Bestia.Signal.ENGINE_CAST_ITEM, this._onCastItem.bind(this));

};
