import DamageEntity from './DamageEntity.js';
import DamageStyle from '.DamageStyle.js';
	
export default class DamageCriticalEntity extends DamageEntity {
	constructor(game, pos, dmg) {

		// no op.

	}
	
	/**
	 * Creates the visual for a critical strike damage.
	 * 
	 * @param {Number}
	 *            dmg - Number to be displayed.
	 * @private
	 * @method Bestia.Engine.Entity.Damage#_display
	 */
	_createVisual(posX, posY) {
		this._sprite = this._game.add.text(posX, posY, '', DamageStyle.STYLE_CRIT);
	}
}