/**
 * Managing the entities must be done via two ways: entites can have an pbId and
 * they can have only a uuid. Entities must be accesable both ways.
 * 
 * @class Bestia.Engine.EntityCacheManager
 */
Bestia.Engine.EntityCacheManager = function() {

	this._pbIdCache = new Bestia.Engine.EntityCache();
	this._uuidCache = new Bestia.Engine.EntityCache();

};

/**
 * Adds an entity to the cache.
 */
Bestia.Engine.EntityCacheManager.prototype.addEntity = function(entity) {

	if (entity.playerBestiaId !== undefined) {
		this._pbIdCache.addEntity(entity.playerBestiaId, entity);
	}

	this._uuidCache.addEntity(entity.uuid, entity);
};

/**
 * Returns the bestia for the given entity uuid.
 */
Bestia.Engine.EntityCacheManager.prototype.getByUuid = function(uuid) {
	return this._uuidCache.getEntity(uuid);
};

/**
 * Returns the bestia for the given player bestia id. Or null if no bestia was
 * found.
 */
Bestia.Engine.EntityCacheManager.prototype.getByPlayerBestiaId = function(pbId) {
	return this._pbIdCache.getEntity(pbId);
};

/**
 * Removes the entity from the cache.
 * 
 * @param entity
 */
Bestia.Engine.EntityCacheManager.prototype.removeEntity = function(entity) {

	if (entity.playerBestiaId !== undefined) {
		this._pbIdCache.removeEntity(entity.playerBestiaId);
	}

	this._uuidCache.removeEntity(entity.uuid);

};

/**
 * Clears the complete cache.
 */
Bestia.Engine.EntityCacheManager.prototype.clear = function() {
	this._pbIdCache.clear();
	this._uuidCache.clear();
};

/**
 * Returns all entities inside this cache.
 * 
 * @returns
 */
Bestia.Engine.EntityCacheManager.prototype.getAllEntities = function() {
	return this._uuidCache.getAllEntities();
};