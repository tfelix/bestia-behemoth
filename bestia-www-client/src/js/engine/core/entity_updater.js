/**
 * The updater will hook into the messaging system and listen for entity update
 * messages. If such a message is received it is responsible for updating and
 * translating this change message to commands for the bestia engine.
 * If updates to entities are detected this will be communicated "upstream" via the
 * use of callback function. The engine/state can hook into the updater via them.
 * 
 * @param {Function}
 *            onAppearFn - An onAppear-Handler when an entity is added to the system.
 *            This handler should return some kind of entity which will be used
 *            to be stored inside the cache.
 * @param {Bestia.PubSub}
 *            pubsub - Reference to the bestia publish/subscriber system for
 *            hooking into update calls.
 */
Bestia.Engine.EntityUpdater = function(pubsub, onAppearFn) {

	if(onAppearFn === undefined) {
		throw "onAppear callback can not be null.";
	}
	
	var self = this;

	/**
	 * Holds all spawned or managed entities.
	 * 
	 * @private
	 */
	this._cache = {};

	/**
	 * The cache for the entities with player bestia ids.
	 * 
	 * @private
	 */
	this._pbidCache = {};

	/**
	 * Holds references to callback function.
	 */
	this._callbacks = {};

	var onMessageHandler = function(_, msg) {
		
		var entities = msg.e;
		
		for(var i = 0; i < entities.length; i++) {
			self._update(entities[i]);
		}
	};
	pubsub.subscribe('map.entites', onMessageHandler);
	
	this.addHandler('onAppear', onAppearFn);
};

/**
 * Adds a callback handler to the entity events on this updates. Use this to get
 * recognized and react upon changes in the updater. The callbackfunction will
 * get the JSON data of the offending entity aswell as a reference to this
 * updater.
 * <p>
 * Attention: The onAppear Handler should return some kind of object which
 * should be persistet inside the cache as this entity. Usually this would be an
 * instance of Bestia.Engine.Entity.
 * </p>
 * 
 * @param {String}
 *            type - Type of the callback. [onAppear|onVanish|onUpdate]
 * @param {Function}
 *            fx - Callback function. Upon invocation it gets two arguments. The
 *            JSON entity object as well as a reference to his updater.
 */
Bestia.Engine.EntityUpdater.prototype.addHandler = function(type, fn) {
	type = type.toLowerCase();
	switch (type) {
	case 'onappear':
	case 'onvanish':
	case 'onupdate':
		this._callbacks[type] = fn;
		break;
	default:
		throw "Unknown function handler! Use onAppear, onVanish or onUpdate only.";
	}
};

/**
 * Decides which action to take for a given entity from the server.
 * 
 * @method Bestia.Engine.EntityUpdater#_update
 * @private
 */
Bestia.Engine.EntityUpdater.prototype._update = function(obj) {
	console.trace('Updating entity: ' + JSON.stringify(obj));

	if (obj.a === 'APPEAR') {

		this._addGameEntity(obj);

	} else if (obj.a === 'VANISH') {

		this._removeGameEntity(obj);

	} else if (obj.a === 'UPDATE') {

		this._updateGameEntity(obj);

	}
};

/**
 * Look up the cache to see if the entity is already inside it.
 */
Bestia.Engine.EntityUpdater.prototype._getGameEntity = function(obj) {
	if (this._cache.hasOwnProperty(obj.uuid)) {
		return this._cache[obj.uuid];
	}

	// Create a new entity.
	this._addGameEntity(obj);
};

/**
 * Adds a new entity/game object to the game itself.
 * 
 * @private
 * @method Bestia.Engine.EntityUpdater#_addGameEntity
 */
Bestia.Engine.EntityUpdater.prototype._addGameEntity = function(obj) {
	// Safeguard if UUID is already inside.
	if (this.getEntityByUuid(obj.uuid) !== null) {
		// Update instead.
		this._updateGameEntity(obj);
		return;
	}

	if (this._callbacks['onappear']) {
		var entity = this._callbacks['onappear'](obj, this);
		
		if(entity === undefined) {
			return;
		}
		
		// Add to cache.
		this._cache[obj.uuid] = entity;
		
		if(obj.pbid !== undefined) {
			this._pbidCache[obj.pbid] = entity;
		}
	}
};

Bestia.Engine.EntityUpdater.prototype._removeGameEntity = function(obj) {

	// Safeguard if UUID is already inside.
	if (this.getEntityByUuid(obj.uuid) === null) {
		return;
	}

	// Remove the entity from the game.
	this._cache[obj.uuid].remove();
	delete this._cache[obj.uuid];

	if (obj.pbid !== undefined) {
		delete this._pbidCache[obj.pbid];
	}

	if (this._callbacks['onvanish']) {
		this._callbacks['onvanish'](obj, this);
	}
};

/**
 * <p>
 * Updates an existing entity. The method will do some pretty tricky stuff. It
 * will validate the current client position with the position which the server
 * has send. It will correct differences in a subtle way.
 * </p>
 * If the positions are equal nothing will be done. If the server requests a
 * special animation this will be shown as well.
 * 
 * @private
 * @method Bestia.Engine.EntityUpdater#_updateGameEntity
 * @param {Object}
 *            obj - Object holding a bestia update message.
 */
Bestia.Engine.EntityUpdater.prototype._updateGameEntity = function(obj) {

	// If no entity with this id is in the cache. Abort.
	if (obj.uuid === undefined || this._cache[obj.uuid] === undefined) {
		return;
	}
	
	var entity = this._cache[obj.uuid];

	if (this._callbacks['onupdate']) {
		this._callbacks['onupdate'](entity, obj, this);
	}
};

/**
 * Returns the entity with the given uuid.
 * 
 * @public
 * @method Bestia.Engine.EntityUpdate#getEntityByUuid
 * @param {String}
 *            uuid - Unique id of the entity which is requested.
 * @return {Bestia.Engine.Entity} The entity which was found or null.
 */
Bestia.Engine.EntityUpdater.prototype.getEntityByUuid = function(uuid) {
	// If no entity with this id is in the cache. Abort.
	if (this._cache[uuid] === undefined) {
		return null;
	} else {
		return this._cache[uuid];
	}
};

/**
 * Returns the entity with the given player bestia id.
 * 
 * @public
 * @method Bestia.Engine.EntityUpdate#getEntityByBestiaId
 * @param {Number}
 *            pbid - Player bestia id of the entity which is requested.
 * @return {Bestia.Engine.Entity} The entity which was found or null.
 */
Bestia.Engine.EntityUpdater.prototype.getEntityByBestiaId = function(pbid) {
	// If no entity with this id is in the cache. Abort.
	if (this._pbidCache[pbid] === undefined) {
		return null;
	} else {
		return this._pbidCache[pbid];
	}
};
