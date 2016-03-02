Bestia.Engine.Entities = Bestia.Engine.Entities || {};

/**
 * Spawns and displays the entities damage in the engine. Object pooling should
 * be used in order to speed up the performance. The underlying sprites are not
 * destroyed if not specified so the damage can be reused later.
 * 
 * @class Bestia.Engine.FX.Damage
 */
Bestia.Engine.Entities.Damage = function(game, pos, dmg) {

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
Bestia.Engine.Entities.Damage.prototype.restart = function(damage, x, y) {

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
Bestia.Engine.Entities.Damage.prototype.destroy = function() {

};

/**
 * 
 * @param {Number}
 *            dmg - Number to be displayed.
 * @private
 * @method Bestia.Engine.Entity.Damage#_display
 */
Bestia.Engine.Entities.Damage.prototype._createVisual = function(posX, posY) {
	this._sprite = this._game.add.text(posX, posY - 50, '', Bestia.Engine.FX.Damage.STYLE_NORMAL);
};

/**
 * Adds an animation to the newly created visual of the damage.
 * 
 * @private
 * @method Bestia.Engine.Entity.Damage#_animateVisual
 */
Bestia.Engine.Entities.Damage.prototype._show = function() {

	var tween = this._game.add.tween(this._sprite).to({
		x : [ this._sprite.x, this._sprite.x - 85 ],
		y : [ this._sprite.y - 60, this._sprite.y - 120 ]
	}, 1000);
	tween.interpolation(function(v, k) {
		return Phaser.Math.bezierInterpolation(v, k);
	});
	this._game.add.tween(this._sprite).to({
		alpha : 0
	}, 100, Phaser.Easing.Linear.None, true, 900).start();

	tween.start();
};

Bestia.Engine.Entities.Damage.STYLE_NORMAL = {
	font : "18px Arial",
	fill : "#ffffff",
	align : "center",
	stroke : '#000000',
	strokeThickness : 3
};

Bestia.Engine.Entities.Damage.STYLE_CRIT = {
	font : "18px Arial",
	fill : "#ffffff",
	align : "center",
	stroke : '#000000',
	strokeThickness : 3
};

Bestia.Engine.Entities.Damage.STYLE_HEAL = {
	font : "18px Arial",
	fill : "#ffffff",
	align : "center",
	stroke : '#000000',
	strokeThickness : 3
};


Bestia.Engine.Entities.MissDamage = function(game, pos, dmg) {
	Bestia.Engine.FX.Damage.call(this, game, pos, dmg);

	// no op.

};

Bestia.Engine.Entities.MissDamage.prototype = Object.create(Bestia.Engine.Entities.Damage.prototype);
Bestia.Engine.Entities.MissDamage.prototype.constructor = Bestia.Engine.Entities.MissDamage.MissDamage;

/**
 * Adds an animation to the newly created visual of the damage.
 * 
 * @private
 * @method Bestia.Engine.Entity.Damage#_animateVisual
 */
Bestia.Engine.Entities.MissDamage.prototype._show = function() {

	var tween = this._game.add.tween(this._sprite).to({
		y : this._sprite.y - 200
	}, 1000);

	this._game.add.tween(this._sprite).to({
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
Bestia.Engine.Entities.MissDamage.prototype._createVisual = function(posX, posY) {
	this._sprite = this._game.add.text(posX, posY, '', Bestia.Engine.FX.Damage.STYLE_NORMAL);
};

Bestia.Engine.Entities.HealDamage = function(game, pos, dmg) {
	Bestia.Engine.FX.MissDamage.call(this, game, pos, dmg);

	// no op.

};

Bestia.Engine.Entities.HealDamage.prototype = Object.create(Bestia.Engine.Entities.MissDamage.prototype);
Bestia.Engine.Entities.HealDamage.prototype.constructor = Bestia.Engine.Entities.HealDamage;

/**
 * 
 * @param {Number}
 *            dmg - Number to be displayed.
 * @private
 * @method Bestia.Engine.Entity.Damage#_display
 */
Bestia.Engine.Entities.HealDamage.prototype._createVisual = function(posX, posY) {
	this._sprite = this._game.add.text(posX, posY, '', Bestia.Engine.FX.Damage.STYLE_HEAL);
};

Bestia.Engine.Entities.CriticalDamage = function(game, pos, dmg) {
	Bestia.Engine.FX.Damage.call(this, game, pos, dmg);

	// no op.
	
};

Bestia.Engine.Entities.CriticalDamage.prototype = Object.create(Bestia.Engine.Entities.Damage.prototype);
Bestia.Engine.Entities.CriticalDamage.prototype.constructor = Bestia.Engine.Entities.CriticalDamage;


/**
 * 
 * @param {Number}
 *            dmg - Number to be displayed.
 * @private
 * @method Bestia.Engine.Entity.Damage#_display
 */
Bestia.Engine.Entities.CriticalDamage.prototype._createVisual = function(posX, posY) {
	this._sprite = this._game.add.text(posX, posY, '', Bestia.Engine.FX.Damage.STYLE_CRIT);
};