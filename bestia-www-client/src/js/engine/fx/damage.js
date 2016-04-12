Bestia.Engine.FX = Bestia.Engine.FX || {};

/**
 * 
 * @class Bestia.Engine.FX.Damage
 */
Bestia.Engine.FX.Damage = function(ctx) {

	this._game = ctx.game;
	this._entityCache = ctx.entityCache;
	this._pubsub = ctx.pubsub;

	ctx.pubsub.subscribe(Bestia.MID.ENTITY_DAMAGE, this._onEntityDamageHandler.bind(this));
};

/**
 * Handles the incoming dialog message.
 * 
 * @private
 * @param _
 * @param msg
 */
Bestia.Engine.FX.Damage.prototype._onEntityDamageHandler = function(_, msg) {
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
			dmgFx = new Bestia.Engine.Entities.HealDamage(this._game, entity.positionPixel, x.dmg);
			break;
		case 'HIT':
			dmgFx = new Bestia.Engine.Entities.Damage(this._game, entity.positionPixel, x.dmg);
			break;
		case 'CRITICAL':
			dmgFx = new Bestia.Engine.Entities.CriticalDamage(this._game, entity.positionPixel, x.dmg);
			break;
		case 'MISS':
			dmgFx = new Bestia.Engine.Entities.MissDamage(this._game, entity.positionPixel, x.dmg);
			break;
		default:
			console.debug('Unknown damage type: ' + x.t);
			return;
		}
		
	}, this);
};