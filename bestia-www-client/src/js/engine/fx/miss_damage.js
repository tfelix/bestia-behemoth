Bestia.Engine.FX = {} || Bestia.Engine.FX;

Bestia.Engine.FX.MissDamage = function(game, pos, dmg) {
	Bestia.Engine.FX.Damage.call(this, game, pos, dmg);

	// no op.

};

Bestia.Engine.FX.MissDamage.prototype = Object.create(Bestia.Engine.FX.Damage.prototype);
Bestia.Engine.FX.MissDamage.prototype.constructor = Bestia.Engine.FX.MissDamage;

/**
 * Adds an animation to the newly created visual of the damage.
 * 
 * @private
 * @method Bestia.Engine.Entity.Damage#_animateVisual
 */
Bestia.Engine.FX.MissDamage.prototype._show = function() {

	var tween = this.game.add.tween(this._sprite).to({
		y : this._sprite.y - 200
	}, 1000);

	this.game.add.tween(this._sprite).to({
		alpha : 0
	}, 100, Phaser.Easing.Linear.None, true, 800).start();

	tween.start();
};

/**
 * 
 * @param {Number}
 *            dmg - Number to be displayed.
 * @private
 * @method Bestia.Engine.Entity.Damage#_display
 */
Bestia.Engine.FX.MissDamage.prototype._createVisual = function(posX, posY) {
	this._sprite = this.game.add.text(posX, posY, '', Bestia.Engine.FX.Damage.STYLE_NORMAL);
};