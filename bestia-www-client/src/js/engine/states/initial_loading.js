Bestia.Engine.States = Bestia.Engine.States || {};

/**
 * The state is triggered for the first game loading. A real loading screen will
 * be shown but since we need to load more data then the normal loading screen
 * we need to load basic game assets.
 * 
 * @constructor
 * @class Bestia.Engine.States.InitialLoadingState
 */
Bestia.Engine.States.InitialLoadingState = function(engine) {

	this.url = engine.ctx.url;
	this._pubsub = engine.ctx.pubsub;

};

Bestia.Engine.States.InitialLoadingState.prototype.init = function() {

};

Bestia.Engine.States.InitialLoadingState.prototype.preload = function() {

	this.game.load.image('castindicator_small', this.url.getIndicatorUrl('_big'));
	this.game.load.image('castindicator_medium', this.url.getIndicatorUrl('_medium'));
	this.game.load.image('castindicator_big', this.url.getIndicatorUrl('_small'));
	
	this.game.load.image('default_item', this.url.getItemIconUrl('_default'));
	
	// #### Filters
	this.game.load.script('filter_blur_x', this.url.getFilterUrl('BlurX'));
	this.game.load.script('filter_blur_y', this.url.getFilterUrl('BlurY'));
	
	this.game.load.spritesheet('rain', this.url.getSpriteUrl('rain'), 17, 17);
	
	// TODO An dieser Stelle bereits die ganzen FX Manager und Indicator Manager die benötigten Daten Laden.
	// TODO das hier in den  Cursor Indicator überführen.
	this.game.load.spritesheet('cursor', this.url.getIndicatorUrl('cursor'), 32, 32);

};

Bestia.Engine.States.InitialLoadingState.prototype.create = function() {

	this._pubsub.publish(Bestia.Signal.ENGINE_INIT_LOADED);
};
