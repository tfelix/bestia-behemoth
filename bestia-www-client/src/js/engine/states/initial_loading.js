Bestia.Engine.States = Bestia.Engine.States || {};

/**
 * The state is triggered for the first game loading. A real loading screen will
 * be shown but since we need to load more data then the normal loading screen
 * we need to load basic game assets.
 * 
 * @constructor
 * @class Bestia.Engine.States.InitialLoadingState
 */
Bestia.Engine.States.InitialLoadingState = function(urlHelper) {

	this.url = urlHelper;

};

Bestia.Engine.States.InitialLoadingState.prototype.init = function() {

};

Bestia.Engine.States.InitialLoadingState.prototype.preload = function() {

	this.game.load.image('castindicator_small', this.url.getIndicatorUrl('_big'));
	this.game.load.image('castindicator_medium', this.url.getIndicatorUrl('_medium'));
	this.game.load.image('castindicator_big', this.url.getIndicatorUrl('_small'));
	
	this.game.load.image('default_item', this.url.getItemIconUrl('_default'));
	
	this.game.load.script('filter_blur_x', this.url.getFilterUrl('BlurX'));
	this.game.load.script('filter_blur_y', this.url.getFilterUrl('BlurY'));
	
	this.game.load.spritesheet('rain', this.url.getSpriteUrl('rain'), 17, 17);

};

Bestia.Engine.States.InitialLoadingState.prototype.create = function() {

	// / / Switch the loading state of the game state.
	this.game.state.start('connecting');
};
