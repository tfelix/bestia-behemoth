Bestia.Engine.FX = {} || Bestia.Engine.FX;
	

Bestia.Engine.FX.CriticalDamage = function(game, pos, dmg) {
	Bestia.Engine.FX.Damage.call(this, game, pos, dmg);

	// no op.
	
};

Bestia.Engine.FX.CriticalDamage.prototype = Object.create(Bestia.Engine.FX.Damage.prototype);
Bestia.Engine.FX.CriticalDamage.prototype.constructor = Bestia.Engine.FX.CriticalDamage;


/**
 * 
 * @param {Number}
 *            dmg - Number to be displayed.
 * @private
 * @method Bestia.Engine.Entity.Damage#_display
 */
Bestia.Engine.FX.CriticalDamage.prototype._createVisual = function(posX, posY) {
	this._sprite = this.game.add.text(posX, posY, '', Bestia.Engine.FX.Damage.STYLE_CRIT);
};