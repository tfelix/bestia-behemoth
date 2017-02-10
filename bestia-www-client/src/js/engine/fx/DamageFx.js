import MID from '../../io/messages/MID.js';
import TextEntity from '../entities/TextEntity';
import {NORMAL, CRIT, HEAL} from './DamageStyle';
import LOG from '../../util/Log';


const CACHE_KEY = 'fxDamage';

/**
 * Displays an text entity for the damage visual effect if a damage message
 * arrives and after the display it will be cached to the fx manager.
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
	 * appropriate place.
	 * 
	 * @private
	 * @param _
	 * @param msg
	 *            The incoming message form the server.
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
				dmgFx = this._game.make.sprite(0,0, x.dmg);
			}
			
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
				break;
			default:
				LOG.warn('Unknown damage type. Using normal style.');
				dmgFx.setStyle(NORMAL);
			break;
			}
				
		}, this);
	}
	
	_startAnimation(targetEntity, dmg) {
		
		let targetSize = targetEntity.getSize();
		let pos = targetEntity.getPositionPx();
		
		let cords = {
			x: [pos.x, (pos.x - targetSize.x - targetSize.x * 0.5)],
			y: [pos.y - targetSize.y * 0.80, pos.y - targetSize.y * 1.5]
		};
		var tween = this._game.add.tween(dmg.getRootVisual()).to(cords, 1000);
		tween.interpolation(function(v,k){
			return Phaser.Math.bezierInterpolation(v, k);
		});
		tween.start();
		this._game.add
			.tween(dmg.getRootVisual())
			.to({alpha: 0}, 100, Phaser.Easing.Linear.None, true, 900)
			.start();Meine 
	}
	
	_createAnimation() {
		
		var tween = this._game.add.tween(this._);
		
	}
}