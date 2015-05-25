/**
 * Spawns and displays the entitys damage in the engine.
 * 
 * @class Bestia.Engine.Entity.Damage
 */
Bestia.Engine.Entity.Damage = function(game, entity, dmg) {
	
	var self = this;
	this.game = game;

	this.normalStyle = {
		font : "18px Arial",
		fill : "#ffffff",
		align : "center",
		stroke : '#000000',
		strokeThickness : 3
	};

	this.critStyle = {
		font : "18px Arial",
		fill : "#ffffff",
		align : "center",
		stroke : '#000000',
		strokeThickness : 3
	};

	this.healStyle = {
		font : "18px Arial",
		fill : "#ffffff",
		align : "center",
		stroke : '#000000',
		strokeThickness : 3
	};

	// get the position of the entity to which the damage display will be
	// attached.
	var posX = entity.centerX - entity.getSizeX / 2 - 10;
	var posY = entity.centerY;
	
	if(dmg.dmgs === null) {
		var visual = this._createVisual(dmg.t, dmg.bc, posX, posY);
		this._animateVisual(visual);
	} else {
		// Create multiple visual for the damage.
		dmg.dmgs.forEach(function(obj) {
			var visual = self._createVisual(obj.d, ob.bc, posX, posY);
			this._animateVisual(visual);
		});
	}
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
	if(bCritical === true) {
		style = this.critStyle;
	} else if(dmg < 0) {
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
Bestia.Engine.Entity.Damage.prototype._animateVisual = function(visual) {
	
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