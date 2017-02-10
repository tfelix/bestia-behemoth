import MID from '../../io/messages/MID.js';
import TextEntity from '../entities/TextEntity';
import {NORMAL, CRIT, HEAL} from './DamageStyle';
import LOG from '../../util/Log';


const CACHE_KEY = 'fxDamage';

/**
 * 
 * @class Bestia.Engine.FX.Damage
 */
export default class DamageFx {
	
	constructor(manager) {

		this._manager = manager;
		this._pubsub = manager.ctx.pubsub;
		this._game = manager.game;
	
		ctx.pubsub.subscribe(MID.ENTITY_DAMAGE, this._handlerOnEntityDamage.bind(this));
	}
	
	/**
	 * Handles the incoming damage massage and displays a damage object at the
	 * apropriate place.
	 * 
	 * @private
	 * @param _
	 * @param msg The incoming message form the server.
	 */
	_handlerOnEntityDamage(_, msg) {

		var dmgs = msg.d;
		dmgs.forEach(function(x) {
			
			// See if there is an entity existing to display this damage.
			var entity = this._entityCache.getByUuid(x.eid);
			
			// No entity, no dmg.
			if(entity === null) {
				return;
			}
			
			var dmgFx = this._manager.getCachedEffect(CACHE_KEY);
			
			if(dmgFx) {
				// Only modify the cached version.
				dmgFx.setText(x.dmg);
				
			} else {
				// Create a new instance of the entity.

			}
			
			//dmgFx = new HealEntity(this._game, entity.positionPixel, x.dmg);
			
			switch (x.t) {
			case 'HEAL':
				dmgFx.setStyle(HEAL);
				break;
			case 'MISS':
			case 'HIT':
				dmgFx.setStyle(NORMAL);
				break;
			case 'CRITICAL':
				dmgFx.setStyle(CRIT);
			default:
				LOG.warn('Unknown ');
				return;
			}
			
		}, this);
	}
}