Bestia.Engine.Indicator = Bestia.Engine.Indicator || {};

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
Bestia.Engine.Indicator.AttackCast = function(manager) {
	Bestia.Engine.Indicator.Basic.call(this, manager);

	// TODO Marker vorbereiten.
	this._marker = null;
	
	// Listen for activation signal.
	this.pubsub.subscribe(Bestia.Signal.ENGINE_CAST_ITEM, this._onCastAttack.bind(this));

};

Bestia.Engine.Indicator.AttackCast.prototype = Object.create(Bestia.Engine.Indicator.Basic.prototype);
Bestia.Engine.Indicator.AttackCast.prototype.constructor = Bestia.Engine.Indicator.AttackCast;


Bestia.Engine.Indicator.Basic.prototype._onClick = function(pointer) {
	// TODO Cast the item on the given spot.
	alert("Attacke Gecasted");
	
	// Forfeit control.
	this._manager.showStandardIndicator();
};

Bestia.Engine.Indicator.ItemCast.prototype._onCastAttack = function(_, attack) {
	// Asks to get activated.
	this._requestActive();

	// Prepare the needed dynamic cast indicator.
	
	// Aktivieren.
	this._requestActive();
};