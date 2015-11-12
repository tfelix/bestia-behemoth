/**
 * The entity cache holds references to entities with their unique id as a key.
 * If an update of the entity is send from the server it can be looked up from
 * the cache.
 */
Bestia.Engine.EntityCache = function() {

	this._cache = {};

};

Bestia.Engine.EntityCache.prototype.addEntity = function(id, entity) {

	this._cache[id] = entity;

};

/**
 * Returns the entity which is registered for the given id. Or NULL if no entity
 * was registered with this ID.
 * 
 * @param id
 *            The unique ID of this entity.
 */
Bestia.Engine.EntityCache.prototype.getEntity = function(id) {
	if (!this._cache.hasOwnProperty(id)) {
		return null;
	}

	return this._cache[id];
};

/**
 * Removes the entity from the cache.
 * 
 * @param entity
 */
Bestia.Engine.EntityCache.prototype.removeEntity = function(id) {

	delete this._cache[id];

};

Bestia.Engine.EntityCache.prototype.clear = function() {
	this._cache = {};
};