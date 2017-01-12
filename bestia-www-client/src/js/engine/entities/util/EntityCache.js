import Signal from '../../../io/Signal';

/**
 * The entity cache holds references to entities with their unique id as a key.
 * If an update of the entity is send from the server it can be looked up from
 * the cache.
 */
export default class EntityCache {
	
	/**
	 * Ctor.
	 */
	constructor(pubsub) {
		
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
		
		pubsub.publish(Signal.ENGINE_SETREF, this);
		pubsub.subscribe(Signal.ENGINE_GET_ENTITY, this._getEntity, this);
	}
	
	get NAME() {
		return this.constructor.NAME;
	}
	
	/**
	 * Returns the entity as pubsub callback.
	 */
	_getEntity(_, msg) {
		
		let e = getEntity(msg.id);
		msg.callback(e);
		
	}

	/**
	 * Adds an entity to the cache with the given identifier.
	 * 
	 * @param {String|Number}
	 *            id - Identifier of the entity.
	 * @param {Bestia.Engine.BasicEntity}
	 *            entity - The entity to add to the cache.
	 */
	addEntity(entity) {
	
		this._entityCache = null;
		this._cache[entity.id] = entity;
	
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

EntityCache.NAME = 'entityCache';