/**
 * The entity cache holds references to entities with their unique id as a key.
 * If an update of the entity is send from the server it can be looked up from
 * the cache.
 * In this version of the cache only the raw data from the server is saved and it
 * has to be manually synchronized with the engine graphics inside a update step
 * towards the engine graphics.
 */
export default class EntityCacheEx {
	
	/**
	 * Ctor.
	 */
	constructor() {
		
		this._cache = {};
	
		/**
		 * Holds an array with all entities in the cache. This is used if all
		 * entities are queried for iterate over them which happens frequently
		 * by the engine. This cache is invalidated when underlying data
		 * changes.
		 * 
		 * @private
		 */
		this._entityCache = null;
	}

	/**
	 * Adds an entity to the cache with the given identifier.
	 * 
	 * @param {String|Number}
	 *            id - Identifier of the entity.
	 */
	addEntity(entity) {
	
		this._entityCache = null;
		this._cache[entity.eid] = entity;
	
	}
	
	/**
	 * Returns the entity which is registered for the given id. Or NULL if no
	 * entity was registered with this ID.
	 * 
	 * @param {String|Number}
	 *            id - The unique ID of this entity.
	 * @return The entity from the cache or NULL if none was found.
	 */
	getEntity(id) {
		if (!this._cache.hasOwnProperty(id)) {
			return null;
		}
	
		return this._cache[id];
	}
	
	/**
	 * Removes the entity from the cache.
	 * 
	 * @param entity
	 */
	removeEntity(id) {
		
		this._entityCache = null;
		delete this._cache[id];
	
	}
	
	/**
	 * Clears the complete cache.
	 */
	clear() {
		this._entityCache = null;
		this._cache = {};
	}
	
	/**
	 * Returns all entity which are currently cached inside this cache so one
	 * can iterate over them.
	 */
	getAllEntities() {
		if (this._entityCache === null) {
			// Populate cache.
			this._entityCache = [];
			for ( var key in this._cache) {
				this._entityCache.push(this._cache[key]);
			}
		}
	
		return this._entityCache;
	}

}