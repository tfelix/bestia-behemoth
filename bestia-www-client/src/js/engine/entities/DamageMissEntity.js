import DamageEntity from './DamageEntity.js';
import DamageStyle from './DamageStyle.js';


/**
 * Displays a missed damage hit to the player.
 */
export default class MissDamageEntity extends DamageEntity {
	constructor(game, pos, dmg) {
		super(game, pos, dmg);
	}
	
	/**
	 * Adds an animation to the newly created visual of the damage.
	 * 
	 * @private
	 * @method Bestia.Engine.Entity.Damage#_animateVisual
	 */
	_show() {

		var tween = this._game.add.tween(this._sprite).to({
			y : this._sprite.y - 200
		}, 1000);

		this._game.add.tween(this._sprite).to({
			alpha : 0
		}, 100, Phaser.Easing.Linear.None, true, 800).start();

		tween.start();
	}

	/**
	 * 
	 * @param {Number}
	 *            dmg - Number to be displayed.
	 * @private
	 * @method Bestia.Engine.Entity.Damage#_display
	 */
	_createVisual(posX, posY) {
		this._sprite = this._game.add.text(posX, posY, '', DamageStyle.STYLE_NORMAL);
	};
} 
