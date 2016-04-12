import EntityCache from './EntityCache.js';

/**
 * Managing the entities must be done via two ways: entites can have an pbId and
 * they can have only a uuid. Entities must be accesable both ways.
 * 
 * @class Bestia.Engine.EntityCacheManager
 */
export default class EntityCacheManager {
	
	constructor() {

		this._pbIdCache = new EntityCache();
		this._uuidCache = new EntityCache();

	}
	
	/**
	 * Adds an entity to the cache.
	 */
	addEntity(entity) {

		if (entity.playerBestiaId !== undefined) {
			this._pbIdCache.addEntity(entity.playerBestiaId, entity);
		}

		this._uuidCache.addEntity(entity.uuid, entity);
	}

	/**
	 * Returns the bestia for the given entity uuid.
	 */
	getByUuid(uuid) {
		return this._uuidCache.getEntity(uuid);
	}

	/**
	 * Returns the bestia for the given player bestia id. Or null if no bestia was
	 * found.
	 */
	getByPlayerBestiaId(pbId) {
		return this._pbIdCache.getEntity(pbId);
	}

	/**
	 * Removes the entity from the cache.
	 * 
	 * @param entity
	 */
	removeEntity(entity) {

		if (entity.playerBestiaId !== undefined) {
			this._pbIdCache.removeEntity(entity.playerBestiaId);
		}

		this._uuidCache.removeEntity(entity.uuid);
	}

	/**
	 * Clears the complete cache.
	 */
	clear() {
		this._pbIdCache.clear();
		this._uuidCache.clear();
	}

	/**
	 * Returns all entities inside this cache.
	 * 
	 * @returns
	 */
	getAllEntities() {
		return this._uuidCache.getAllEntities();
	}
}
