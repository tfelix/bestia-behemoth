Bestia.Engine.Indicator = Bestia.Engine.Indicator || {};

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
Bestia.Engine.Indicator.ItemCast = function(manager) {
	Bestia.Engine.Indicator.Basic.call(this, manager);

	// Listen for activation signal.
	this._ctx.pubsub.subscribe(Bestia.Signal.ENGINE_CAST_ITEM, this._onCastItem.bind(this));
};

Bestia.Engine.Indicator.ItemCast.prototype = Object.create(Bestia.Engine.Indicator.Basic.prototype);
Bestia.Engine.Indicator.ItemCast.prototype.constructor = Bestia.Engine.Indicator.ItemCast;

Bestia.Engine.Indicator.ItemCast.prototype._onClick = function() {
	// pointer
	// TODO Cast the item on the given spot.
	alert("Gecasted");
	
	// Forfeit control.
	this._manager.showStandardIndicator();
};

Bestia.Engine.Indicator.ItemCast.prototype._onCastItem = function() {
	// _, item
	
	// Asks to get activated.
	this._requestActive();

	// Prepare the needed dynamic cast indicator.
	
	// Aktivieren.
	this._requestActive();
};

/**
 * Preload all needed assets.
 */
Bestia.Engine.Indicator.ItemCast.prototype.preLoadAssets = function() {
	this._ctx.game.load.image('cursor', this._ctx.url.getIndicatorUrl('cursor'));
	//this._ctx.loader.load({key: 'cast_indicator', url: this._ctx.url.getSpriteUrl('cast_indicator'), type: 'image'});
};

/**
 * Preload all needed assets.
 */
Bestia.Engine.Indicator.ItemCast.prototype.create = function() {
	this._marker = null;
};