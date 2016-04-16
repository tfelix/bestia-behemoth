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

Bestia.Engine.Indicator.ItemCast.prototype._onClick = function(pointer) {

	if(pointer.button === Phaser.Mouse.RIGHT_BUTTON) {
		// Was canceled.
		this._manager.showDefault();
		return;
	}

	// Forfeit control.
	this._manager.showStandardIndicator();
};

Bestia.Engine.Indicator.ItemCast.prototype._onCastItem = function(_, item) {
	// Change the size of the indicator based on the item size.
	this._parseIndicator(item.indicator);

	// Asks to get activated.
	this._requestActive();

	// Prepare the needed dynamic cast indicator.
};

Bestia.Engine.Indicator.ItemCast.prototype._parseIndicator = function(indicatorStr) {
	
};

/**
 * Preload all needed assets.
 */
Bestia.Engine.Indicator.ItemCast.prototype.load = function() {
	this._ctx.game.load.image('cast_indicator', this._ctx.url.getSpriteUrl('cast_indicator'));
};

/**
 * Preload all needed assets.
 */
Bestia.Engine.Indicator.ItemCast.prototype.create = function() {
	this._marker = this._ctx.game.make.sprite(500, 500, 'cast_indicator');
	//this._ctx.groups.overlay.add(this._marker);
	this._marker.anchor.setTo(0.5, 0.5);
	this._marker.angle = 0;
	this._marker.alpha = 0.7;
	this._ctx.game.add.tween(this._marker).to( { angle: 360 }, 1500, Phaser.Easing.Linear.None, true, 0).loop(true);
	
	//this._requestActive();
};