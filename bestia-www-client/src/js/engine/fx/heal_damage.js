Bestia.Engine.FX = {} || Bestia.Engine.FX;

Bestia.Engine.FX.HealDamage = function(game, pos, dmg) {
	Bestia.Engine.FX.MissDamage.call(this, game, pos, dmg);

	// no op.

};

Bestia.Engine.FX.HealDamage.prototype = Object.create(Bestia.Engine.FX.MissDamage.prototype);
Bestia.Engine.FX.HealDamage.prototype.constructor = Bestia.Engine.FX.HealDamage;

/**
 * 
 * @param {Number}
 *            dmg - Number to be displayed.
 * @private
 * @method Bestia.Engine.Entity.Damage#_display
 */
Bestia.Engine.FX.HealDamage.prototype._createVisual = function(posX, posY) {
	this._sprite = this.game.add.text(posX, posY, '', Bestia.Engine.FX.Damage.STYLE_HEAL);
};