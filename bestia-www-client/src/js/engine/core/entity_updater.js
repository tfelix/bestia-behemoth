/**
 * The updater will hook into the messaging system and listen for entity update
 * messages. If such a message is received it is responsible for updating and
 * translating this change message to commands for the bestia engine.
 * 
 * @param {Bestia.Engine.World}
 *            world - A instance of the bestia world holding parsed information
 *            and utility methods of the current map/world.
 */
Bestia.Engine.EntityUpdater = function(game, bestiaWorld) {
	
	this._game = game;
	
	
	this._bestiaWorld = bestiaWorld;
	
	/**
	 * Holds all spawned or managed entities.
	 * @private
	 */
	this._cache = {};

	var onMessageHandler = function(_, msg) {
		msg.e.forEach(this._update);
	};
	// {"uuid":"1460814f-e2a0-467e-898c-7fba8731a57e","s":["mastersmith"],"x":1,"y":11,"t":"NONE"}
	Bestia.subscribe('map.entites', onMessageHandler);
};

/**
 * Decides which action to take for a given entity from the server.
 * 
 * @method Bestia.Engine.EntityUpdater#_update
 * @private
 */
Bestia.Engine.EntityUpdater._update = function(obj) {
	console.trace('Updating entity: ' + JSON.stringify(obj));
	if(obj.a === 'APPEAR') {
		
		this._addGameEntity(obj);
		
	} else if(obj.a === 'VANISH') {
		
		this._updateGameEntity(obj);
		
	} else if(obj.a === 'UPDATE') {
		
		this._updateGameEntity(obj);
		
	}
};

/**
 * Look up the cache to see if the entity is already inside it.
 */
Bestia.Engine.EntityUpdater._getGameEntity = function(obj) {
	if(this._cache.hasOwnProperty(obj.uuid)) {
		return this._cache[obj.uuid];
	}
	
	// Create a new entity.
	this._addGameEntity(obj);
};

Bestia.Engine.EntityUpdater._addGameEntity = function(obj) {
	
	// Add the entity to the engine.
	
	// new Bestia.Engine.Entity(this.game, this._bestiaWorld);
};

Bestia.Engine.EntityUpdater._removeGameEntity = function(obj) {
	
	// Add the entity to the engine.
};

Bestia.Engine.EntityUpdater._updateGameEntity = function(obj) {
	
	// Add the entity to the engine.
};




