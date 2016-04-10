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
Bestia.Engine.FX.EffectsManager = function(pubsub, game, entityCache, groups) {
	if (pubsub === undefined) {
		throw "PubSub can not be undefined";
	}

	if (game === undefined) {
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

	// Add the instances to control certain effects depending on incoming
	// messages.
	this._effectInstances.push(new Bestia.Engine.FX.Damage(pubsub, entityCache, game));
	this._effectInstances.push(new Bestia.Engine.FX.Chat(pubsub, entityCache, game));
	this._effectInstances.push(new Bestia.Engine.FX.Dialog(pubsub, game));
	this._effectInstances.push(new Bestia.Engine.FX.Brightness(pubsub, entityCache, game, groups));
	this._effectInstances.push(new Bestia.Engine.FX.Rain(pubsub, game, groups));
};

Bestia.Engine.FX.EffectsManager.prototype.create = function() {
	this._effectInstances.forEach(function(fx){
		if(fx.create !== undefined) {
			fx.create();
		}
	});
};

Bestia.Engine.FX.EffectsManager.prototype.update = function() {
	this._effectInstances.forEach(function(fx){
		if(fx.update !== undefined) {
			fx.update();
		}
	});
};

Bestia.Engine.FX.EffectsManager.prototype.load = function() {
	this._effectInstances.forEach(function(fx){
		if(fx.load !== undefined) {
			fx.load();
		}
	});
};