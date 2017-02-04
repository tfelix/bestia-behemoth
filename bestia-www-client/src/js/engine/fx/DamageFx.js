import MID from '../../io/messages/MID.js';
import DamageEntity from './DamageEntity.js';
import HealEntity from './HealEntity.js';
import DamageMissEntity from './DamageMissEntity.js';
import DamageCriticalEntity from './DamageCriticalEntity.js';

/**
 * 
 * @class Bestia.Engine.FX.Damage
 */
export default class DamageFx {
	
	constructor(ctx) {

		this._pubsub = ctx.pubsub;
	
		ctx.pubsub.subscribe(MID.ENTITY_DAMAGE, this._handlerOnEntityDamage.bind(this));
	}
	
	/**
	 * Handles the incoming dialog message.
	 * 
	 * @private
	 * @param _
	 * @param msg
	 */
	_handlerOnEntityDamage(_, msg) {
		// TODO Caching implementieren.

		var dmgs = msg.d;
		dmgs.forEach(function(x) {
			
			// See if there is an entity existing to display this damage.
			var entity = this._entityCache.getByUuid(x.uuid);
			
			// No entity, no dmg.
			if(entity === null) {
				return;
			}
			
			var dmgFx = null;
			
			switch (x.t) {
			case 'HEAL':
				dmgFx = new HealEntity(this._game, entity.positionPixel, x.dmg);
				break;
			case 'HIT':
				dmgFx = new DamageEntity(this._game, entity.positionPixel, x.dmg);
				break;
			case 'CRITICAL':
				dmgFx = new DamageCriticalEntity(this._game, entity.positionPixel, x.dmg);
				break;
			case 'MISS':
				dmgFx = new DamageMissEntity(this._game, entity.positionPixel, x.dmg);
				break;
			default:
				console.debug('Unknown damage type: ' + x.t);
				return;
			}
			
		}, this);
	}
}