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
		this._ctx = manager.ctx;
		this._pubsub = manager.ctx.pubsub;
		this._game = manager.ctx.game;
		this._entityCache = manager.ctx.entityCache;
	
		this._pubsub.subscribe(MID.ENTITY_DAMAGE, this._handlerOnEntityDamage.bind(this));
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
			var entity = this._entityCache.getEntity(msg.eid);
			
			// No entity, no dmg.
			if(entity === null) {
				return;
			}
			
			var dmgFx = this._manager.getCachedEffect(CACHE_KEY);
			
			if(!dmgFx) {
				// Create a new instance of the entity.
				dmgFx = new TextEntity(this._ctx);
				dmgFx.addToGame();
			}

			dmgFx.setText(x.dmg);
			
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
		
		// Start the display animation.
		this._startAnimation(entity, dmgFx);
				
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

		// add entity back to cache when tween finished.
		tween.onComplete.add(function(){
			this._manager.cacheEffect(CACHE_KEY, dmg);
		}, this);

		// Add alpha fade.
		this._game.add
			.tween(dmg.getRootVisual())
			.to({alpha: 0}, 100, Phaser.Easing.Linear.None, true, 950)
			.start();
		// Start animation.
		tween.start();
	}
	
	_createAnimation() {
		
		var tween = this._game.add.tween(this._);
		
	}
}