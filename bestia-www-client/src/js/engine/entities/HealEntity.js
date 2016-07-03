import DamageEntity from './DamageEntity.js';
import DamageStyle from './DamageStyle.js';


export default class HealDamage extends DamageEntity {
	constrcutor(game, pos, dmg) {
		super(game, pos, dmg);

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
		this._sprite = this._game.add.text(posX, posY, '', DamageStyle.STYLE_HEAL);
	}
}