Bestia.Engine.FX = Bestia.Engine.FX || {};

/**
 * Spawns and displays the entities damage in the engine. Object pooling should
 * be used in order to speed up the performance. The underlying sprites are not
 * destroyed if not specified so the damage can be reused later.
 * 
 * @class Bestia.Engine.FX.Damage
 */
Bestia.Engine.FX.Damage = function(game, pos, dmg) {

	this._game = game;

	/**
	 * Holds the engine sprite object to later reference it again and possibly
	 * redisplay it.
	 * 
	 * @property
	 * @private
	 */
	this._sprite = null;
	
	this._createVisual(pos.x, pos.y);
	
	this._sprite.text = dmg;
	
	this._show();

};

/**
 * Re-displays the damage object on the given world entity.
 */
Bestia.Engine.FX.Damage.restart = function(damage, x, y) {

	// Visual should be already there. Update with the new data.
	this._sprite.text = damage;
	this._sprite.x = x;
	this._sprite.y = y;
	this._sprite.alpha = 1;
	
	this._show();
	
};

/**
 * Finally destroys the damage visual. Frees all associated memory.
 */
Bestia.Engine.FX.Damage.destroy = function() {

};

/**
 * 
 * @param {Number}
 *            dmg - Number to be displayed.
 * @private
 * @method Bestia.Engine.Entity.Damage#_display
 */
Bestia.Engine.FX.Damage.prototype._createVisual = function(posX, posY) {
	this._sprite = this.game.add.text(posX, posY, '', Bestia.Engine.FX.Damage.STYLE_NORMAL);
};

/**
 * Adds an animation to the newly created visual of the damage.
 * 
 * @private
 * @method Bestia.Engine.Entity.Damage#_animateVisual
 */
Bestia.Engine.FX.Damage.prototype._show = function() {

	var tween = this.game.add.tween(this._sprite).to({
		x : [ this._sprite.x - 10, this._sprite.x - 75 ],
		y : [ this._sprite.y + 150, this._sprite.y - 10 ]
	}, 1000);
	tween.interpolation(function(v, k) {
		return Phaser.Math.bezierInterpolation(v, k);
	});
	this.game.add.tween(this._sprite).to({
		alpha : 0
	}, 100, Phaser.Easing.Linear.None, true, 900).start();

	tween.start();
};

Bestia.Engine.FX.Damage.STYLE_NORMAL = {
	font : "18px Arial",
	fill : "#ffffff",
	align : "center",
	stroke : '#000000',
	strokeThickness : 3
};

Bestia.Engine.FX.Damage.STYLE_CRIT = {
	font : "18px Arial",
	fill : "#ffffff",
	align : "center",
	stroke : '#000000',
	strokeThickness : 3
};

Bestia.Engine.FX.Damage.STYLE_HEAL = {
	font : "18px Arial",
	fill : "#ffffff",
	align : "center",
	stroke : '#000000',
	strokeThickness : 3
};


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