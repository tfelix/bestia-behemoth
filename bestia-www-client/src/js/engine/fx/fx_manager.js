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

	/**
	 * Holds reference to all added effect instances.
	 * 
	 * @private
	 */
	this._effectInstances = [];
	this._entityCache = entityCache;
	this._game = game;

	this._effectInstances.push(new Bestia.Engine.FX.Damage(pubsub, entityCache, game));
	this._effectInstances.push(new Bestia.Engine.FX.Chat(pubsub, entityCache, game));
	this._effectInstances.push(new Bestia.Engine.FX.Dialog(pubsub, game));
};