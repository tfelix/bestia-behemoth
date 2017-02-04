/*global Phaser */

import Entity from '../entities/Entity.js';
import {NORMAL} from './DamageStyle.js';


/**
 * Spawns and displays the entities damage in the engine. Object pooling should
 * be used in order to speed up the performance. The underlying sprites are not
 * destroyed if not specified so the damage can be reused later.
 * 
 * @class Bestia.Engine.FX.Damage
 */
export default class DamageEntity extends Entity {
	constructor(game, pos, dmg) {
		// TODO Das hier fixen.
		super(null, null);
		
		this._game = game;

		/**
		 * Holds the engine sprite object to later reference it again and
		 * possibly redisplay it.
		 * 
		 * @property
		 * @private
		 */
		this._sprite = null;
		
		this._createVisual(pos.x, pos.y);
		
		this._sprite.text = dmg;
		
		this._show();
	}
	
	/**
	 * Re-displays the damage object on the given world entity.
	 */
	restart(damage, x, y) {

		// Visual should be already there. Update with the new data.
		this._sprite.text = damage;
		this._sprite.x = x;
		this._sprite.y = y;
		this._sprite.alpha = 1;
		
		this._show();
		
	}

	/**
	 * Finally destroys the damage visual. Frees all associated memory.
	 */
	destroy() {
		// no op.
	}

	/**
	 * 
	 * @param {Number}
	 *            dmg - Number to be displayed.
	 * @private
	 * @method Bestia.Engine.Entity.Damage#_display
	 */
	_createVisual(posX, posY) {
		this._sprite = this._game.add.text(posX, posY - 50, '', NORMAL);
	}

	/**
	 * Adds an animation to the newly created visual of the damage.
	 * 
	 * @private
	 * @method Bestia.Engine.Entity.Damage#_animateVisual
	 */
	_show() {

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
	}
}