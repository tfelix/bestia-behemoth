/**
 * The factory is responsible for loading all the needed assets to display a
 * certain entity. It resolves if it is a bestia, sprite, item etc. entity and
 * uses the correct javascript class to manage it. It gets added to the entity
 * cache to receive updates.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 */
Bestia.Engine.EntityFactory = function(game, demandLoader, entityCache, groups) {

	this._game = game;

	this._demandLoader = demandLoader;

	this._entityCache = entityCache;
	
	this._groups = groups;

};

/**
 * Creates a bestia entity.
 * 
 * @param data
 *            The update data for this (bestia) entity.
 */
Bestia.Engine.EntityFactory.prototype.createBestiaEntity = function(data) {

	var self = this;
	var entity = new Bestia.Engine.SpriteEntity(self._game, data.uuid, data.x, data.y, data.pbid);
	
	self._entityCache.addEntity(entity);

	this._demandLoader.loadMobSprite(data.s, function() {

		entity.setSprite(data.s);
		entity.addToGroup(self._groups.sprites);

		if (data.a === "APPEAR") {
			entity.appear();
		} else {
			entity.show();
		}
	});

};

/**
 * Creates an item entity on the map for the given update data.
 * 
 * @param data
 *            The update data for this (item) entity.
 */
Bestia.Engine.EntityFactory.prototype.createItemEntity = function(data) {

	var self = this;

	this._demandLoader.loadItemSprite(data.s, function() {

		var entity = new Bestia.Engine.ItemEntity(self._game, data.uuid, data.x, data.y, data.s);
		
		entity.addToGroup(self._groups.sprites);
		self._entityCache.addEntity(entity);

		if (data.a === "APPEAR") {
			entity.appear();
		} else {
			entity.show();
		}
	});

};


Bestia.Engine.EntityFactory.prototype.createPlayerEntity = function(data) {

	var self = this;

	console.error("Not implemented");

};

