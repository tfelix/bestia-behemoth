/**
 * Spawns and displays the entities damage in the engine. Object pooling should
 * be used in order to speed up the performance. The underlying sprites are not
 * destroyed if not specified so the damage can be reused later.
 * 
 * @class Bestia.Engine.Entity.Damage
 */
Bestia.Engine.CG.Damage = function(game, entity, dmg) {

	var self = this;
	this.game = game;

	/**
	 * Holds the engine sprite object to later reference it again and possibly
	 * redisplay it.
	 * 
	 * @property
	 * @private
	 */
	this._sprites = [];

	// get the position of the entity to which the damage display will be
	// attached.
	var posX = entity.centerX - entity.getSizeX / 2 - 10;
	var posY = entity.centerY;

	if (dmg.dmgs === null) {
		var visual = this._createVisual(dmg.t, dmg.bc, posX, posY);
		this._animateVisual(visual);
	} else {
		// Create multiple visual for the damage.
		dmg.dmgs.forEach(function(obj) {
			var visual = self._createVisual(obj.d, obj.bc, posX, posY);
			this._animateVisual(visual);
		});
	}
};

/**
 * Re-displays the damage object on the given world entity.
 */
Bestia.Engine.CG.Damage.restart = function() {

};

/**
 * 
 */
Bestia.Engine.CG.Damage.destroy = function() {

};

/**
 * 
 * @param {Number}
 *            dmg - Number to be displayed.
 * @param {Boolean}
 *            bCritical - Flag if the damage should be displayed as a critical
 *            damage.
 * @private
 * @method Bestia.Engine.Entity.Damage#_display
 */
Bestia.Engine.Entity.Damage.prototype._createVisual = function(dmg, bCritical, posX, posY) {
	var style = this.normalStyle;
	if (bCritical === true) {
		style = this.critStyle;
	} else if (dmg < 0) {
		style = this.healStyle;
	}

	return this.game.add.text(posX, posY, dmg, style);
};

/**
 * Adds an animation to the newly created visual of the damage.
 * 
 * @private
 * @method Bestia.Engine.Entity.Damage#_animateVisual
 */
Bestia.Engine.Entity.Damage.prototype._show = function(visual) {

	var tween = this.game.add.tween(visual).to({
		x : [ visual.x - 10, visual.x - 75 ],
		y : [ visual.y + 150, visual.y - 10 ]
	}, 1000);
	tween.interpolation(function(v, k) {
		return Phaser.Math.bezierInterpolation(v, k);
	});
	this.game.add.tween(visual).to({
		alpha : 0
	}, 100, Phaser.Easing.Linear.None, true, 900).start();

	tween.start();
};

Bestia.Engine.CG.Damage.STYLE_NORMAL = {
	font : "18px Arial",
	fill : "#ffffff",
	align : "center",
	stroke : '#000000',
	strokeThickness : 3
};

Bestia.Engine.CG.Damage.STYLE_CRIT = {
	font : "18px Arial",
	fill : "#ffffff",
	align : "center",
	stroke : '#000000',
	strokeThickness : 3
};

Bestia.Engine.CG.Damage.STYLE_HEAL = {
	font : "18px Arial",
	fill : "#ffffff",
	align : "center",
	stroke : '#000000',
	strokeThickness : 3
};