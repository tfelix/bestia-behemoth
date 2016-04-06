/**
 * The updater will hook into the messaging system and listen for entity update
 * messages. If such a message is received it is responsible for updating and
 * translating this change message to commands for the bestia engine. If updates
 * to entities are detected this will be communicated "upstream" via the use of
 * callback function. The engine/state can hook into the updater via them.
 * <p>
 * The updates of the entities are hold back until releaseHold is called.
 * </p>
 * 
 * @param {Bestia.PubSub}
 *            pubsub - Reference to the bestia publish/subscriber system for
 *            hooking into update calls.
 */
Bestia.Engine.EntityUpdater = function(pubsub, cache, entityFactory) {
	if (pubsub === undefined) {
		throw "PubSub can not be undefined";
	}

	if (cache === undefined) {
		throw "Cache can not be undefined.";
	}

	if (entityFactory === undefined) {
		throw "EntityFactory can not be null.";
	}

	/**
	 * Temporary buffer to hold all the data until releaseHold is called.
	 */
	this._buffer = [];

	this._cache = cache;

	this._pubsub = pubsub;

	this._factory = entityFactory;

	// === SUBSCRIBE ===
	pubsub.subscribe(Bestia.MID.ENTITY_UPDATE, this._onUpdateHandler.bind(this));
	pubsub.subscribe(Bestia.MID.ENTITY_MOVE, this._onMoveHandler.bind(this));
	pubsub.subscribe(Bestia.MID.ENTITY_POSITION, this._onPositionHandler.bind(this));
};

/**
 * Makes a complete update of the entity. Which can be a vanish, create or
 * simple update.
 */
Bestia.Engine.EntityUpdater.prototype._onUpdateHandler = function(_, msg) {
	if (this._isBuffered(msg)) {
		return;
	}

	switch (msg.a) {
	case 'APPEAR':
		// The entity must not exist.
		var entity = this._cache.getByUuid(msg.uuid);
		if (entity !== null) {
			// Exists already. Strange.
			return;
		}

		switch (msg.t) {
		case 'ITEM':
			// let an item appear.
			this._factory.createItemEntity(msg);
			break;
		case 'STATIC':
			// Static sprite appear.
			// this._factory.createStaticEntity(msg);
			break;
		case 'MOB_ANIM':
			// Normal bestia.
			this._factory.createBestiaEntity(msg);
			break;
		case 'PLAYER_ANIM':
			// Player animation sprite.
			//this._factory.createPlayerEntity(msg);
			break;
		}
		break;
	}
};

/**
 * A movement prediction update was send. Plan the animation path to predict the
 * movement of the entity.
 */
Bestia.Engine.EntityUpdater.prototype._onMoveHandler = function(_, msg) {
	if (this._isBuffered(msg)) {
		return;
	}

	var entity = this._cache.getByUuid(msg.uuid);
	// Entity not in cache. We cant do anything.
	if (entity === null) {
		return;
	}

	entity.moveTo(msg);
};

/**
 * A position update was send. Check if the entity is on this place or at least
 * near it. If distance is too far away hard correct it.
 */
Bestia.Engine.EntityUpdater.prototype._onPositionHandler = function(_, msg) {
	if (this._isBuffered(msg)) {
		return;
	}

	var entity = this._cache.getByUuid(msg.uuid);
	// Entity not in cache. We cant do anything.
	if (entity === null) {
		return;
	}

	//entity.checkPosition(msg);
};

/**
 * Checks if the buffering is still active. If this is the case buffer the
 * message and return true else return false.
 * 
 * @param msg
 * @returns {Boolean}
 */
Bestia.Engine.EntityUpdater.prototype._isBuffered = function(msg) {
	if (this._buffer !== undefined) {
		this._buffer.push({
			topic : msg.mid,
			msg : msg
		});
		return true;
	} else {
		return false;
	}
};

/**
 * Holds the entity updates in a cache until the engine has loaded the map. When
 * this method is called all updates are forwarded to the engine.
 */
Bestia.Engine.EntityUpdater.prototype.releaseHold = function() {
	if (this._buffer === undefined) {
		return;
	}

	var temp = this._buffer;
	this._buffer = undefined;
	temp.forEach(function(d) {
		switch (d.topic) {
		case Bestia.MID.ENTITY_UPDATE:
			this._onUpdateHandler(d.topic, d.msg);
			break;
		case Bestia.MID.ENTITY_MOVE:
			this._onMoveHandler(d.topic, d.msg);
			break;
		case Bestia.MID.ENTITY_POSITION:
			this._onPositionHandler(d.topic, d.msg);
			break;
		default:
			break;
		}
	}, this);
};
