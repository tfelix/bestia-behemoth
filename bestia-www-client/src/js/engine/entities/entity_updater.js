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
Bestia.Engine.EntityUpdater = function(pubsub, cache) {
	if (pubsub === undefined) {
		throw "PubSub can not be undefined";
	}

	if (cache === undefined) {
		throw "Cache can not be undefined.";
	}

	var self = this;

	/**
	 * Temporary buffer to hold all the data until releaseHold is called.
	 */
	this._buffer = [];

	this._cache = cache;

	this._pubsub = pubsub;
	
	this._factory = undefined;

	this._onMessageHandler = function(_, msg) {
		if (self._buffer !== undefined) {
			self._buffer.push(msg);
			return;
		}

		var entities = msg.e;

		for (var i = 0; i < entities.length; i++) {
			self._update(entities[i]);
		}
	};
	pubsub.subscribe('map.entites', this._onMessageHandler);
};

/**
 * Decides which action to take for a given entity from the server.
 * 
 * @method Bestia.Engine.EntityUpdater#_update
 * @private
 */
Bestia.Engine.EntityUpdater.prototype._update = function(obj) {
	
	console.trace('Updating entity: ' + JSON.stringify(obj));

	switch (obj.t) {
	case "LOOT":
		this._factory.createItemEntity(obj);
		break;
	case "BESTIA":
		this._factory.createBestiaEntity(obj);
		break;
	default:

		break;
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
	temp.forEach(function(msg) {
		this._onMessageHandler('ignorethis', msg);
	}, this);
};
