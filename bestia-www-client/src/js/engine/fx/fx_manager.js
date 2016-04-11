/**
 * FX Manager which is responsible for effect generation and display. Effects
 * are not so important to be tracked liked entities. In fact they can be
 * generated directly by the server or internally by the client if.
 * 
 * TODO There must be a way to communicate with the effects.
 * 
 * If this class gets too bloated it must be split into smaller fx manager only
 * responsible for their area.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 */
Bestia.Engine.FX.EffectsManager = function(ctx) {
	if (!ctx.pubsub) {
		throw new Error("PubSub can not be undefined");
	}

	if (!ctx.game) {
		throw  new Error("Game can not be null.");
	}

	if (!ctx.entityCache) {
		throw new Error("Cache can not be undefined.");
	}
	
	if (!ctx.groups) {
		throw new Error("Groups an not be undefined.");
	}
	
	if (!ctx.loader) {
		throw new Error("Loader can not be undefined.");
	}

	/**
	 * Holds reference to all added effect instances.
	 * 
	 * @private
	 */
	this._effectInstances = [];
	
	this._ctx = ctx;

	// Add the instances to control certain effects depending on incoming
	// messages.
	this._effectInstances.push(new Bestia.Engine.FX.Damage(ctx));
	this._effectInstances.push(new Bestia.Engine.FX.Chat(ctx));
	//this._effectInstances.push(new Bestia.Engine.FX.Dialog(ctx));
	this._effectInstances.push(new Bestia.Engine.FX.Brightness(ctx));
	this._effectInstances.push(new Bestia.Engine.FX.Rain(ctx));
	this._effectInstances.push(new Bestia.Engine.FX.RangeMeasure(ctx));
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