/**
 * FX Manager which is responsible for effect generation and display. Effects
 * are not so important to be tracked liked entities. In fact they can be
 * generated directly by the server or internally by the client if.
 * 
 * If this class gets too bloated it must be split into smaller fx manager only
 * responsible for their area.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 */
Bestia.Engine.FX.EffectsManager = function(pubsub, game, entityCache) {
	if (pubsub === undefined) {
		throw "PubSub can not be undefined";
	}
	
	if(game === undefined) {
		throw "Game can not be null.";
	}

	if (entityCache === undefined) {
		throw "Cache can not be undefined.";
	}

	this._entityCache = entityCache;
	this._game = game;

	pubsub.subscribe('entity.damage', this._onEntityDmgMsgHandler.bind(this));
};

/**
 * Handles damage FX messages.
 */
Bestia.Engine.FX.EffectsManager.prototype._onEntityDmgMsgHandler = this._onMessageHandler = function(_, msg) {
	// TODO Caching implementieren.

	var dmgs = msg.d;
	dmgs.forEach(function(x) {
		
		// See if there is an entity existing to display this damage.
		var entity = this._entityCache.getByUuid(x.uuid);
		
		// No entity, no dmg.
		if(entity == null) {
			return;
		}
		
		var dmg = null;
		
		switch (x.t) {
		case 'HEAL':
			dmg = new Bestia.Engine.FX.HealDamage(this._game, entity.position, dmg);
			break;
		case 'HIT':
			dmg = new Bestia.Engine.FX.Damage(this._game, entity.position, dmg);
			break;
		case 'CRITICAL':
			dmg = new Bestia.Engine.FX.CriticalDamage(this._game, entity.position, dmg);
			break;
		case 'MISS':
			dmg = new Bestia.Engine.FX.MissDamage(this._game, entity.position, dmg);
			break;
		default:
			console.debug('Unknown damage type: ' + x.t);
			return;
		}
		
	}, this);
};