Bestia.Engine.FX = Bestia.Engine.FX || {};

/**
 * The range measure effect will display a certain range on the ground. It can
 * be used to communicate usable ranges to the user.
 * 
 * @param {Bestia.Engine.EntityCacheManager}
 *            cache
 */
Bestia.Engine.FX.RangeMeasure = function(pubsub, cache, game, groups) {

	this._pubsub = pubsub;

	this._game = game;

	this._cache = cache;

	this._groups = groups;
};

Bestia.Engine.FX.RangeMeasure.prototype.destroy = function() {
	
};

Bestia.Engine.FX.RangeMeasure.prototype.create = function() {

};

Bestia.Engine.FX.RangeMeasure.prototype.update = function() {

};

Object.defineProperty(Bestia.Engine.FX.RangeMeasure.prototype, 'range', {

});

Object.defineProperty(Bestia.Engine.FX.RangeMeasure.prototype, 'show', {

});