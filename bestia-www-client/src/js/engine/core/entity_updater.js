/**
 * The updater will hook into the messaging system and listen for entity update
 * messages. If such a message is received it is responsible for updating and
 * translating this change message to commands for the bestia engine.
 * 
 * @param {Bestia.Engine.World}
 *            world - A instance of the bestia world holding parsed information
 *            and utility methods of the current map/world.
 * @param {Phaser.Game}
 *            game - A reference to the phaser game so new entities can be added
 *            or removed.
 * @param {Bestia.PubSub}
 *            pubsub - Reference to the bestia publish/subscriber system for
 *            hooking into update calls.
 */
Bestia.Engine.EntityUpdater = function(pubsub, game, bestiaWorld) {

	var self = this;

	this._game = game;

	this._bestiaWorld = bestiaWorld;

	/**
	 * Holds all spawned or managed entities.
	 * 
	 * @private
	 */
	this._cache = {};

	var onMessageHandler = function(_, msg) {
		msg.e.forEach(self._update, self);
	};
	pubsub.subscribe('map.entites', onMessageHandler);
};

/**
 * Decides which action to take for a given entity from the server.
 * 
 * @method Bestia.Engine.EntityUpdater#_update
 * @private
 */
Bestia.Engine.EntityUpdater.prototype._update = function(obj) {
	// console.trace('Updating entity: ' + JSON.stringify(obj));

	this._addGameEntity(obj);

	if (obj.a === 'APPEAR') {

		this._addGameEntity(obj);

	} else if (obj.a === 'VANISH') {

		this._updateGameEntity(obj);

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

	// Add the entity to the engine.
	var entity = new Bestia.Engine.Entity(obj, this._game, this._bestiaWorld);
	this._cache[obj.uuid] = entity;

	// Let the sprite appear.
};

Bestia.Engine.EntityUpdater.prototype._removeGameEntity = function(obj) {

	// Remove the entity from the game.
	this._cache[obj.uuid].remove();
	delete this._cache[obj.uuid];
};

Bestia.Engine.EntityUpdater.prototype._updateGameEntity = function(obj) {

	// Add the entity to the engine.
};
