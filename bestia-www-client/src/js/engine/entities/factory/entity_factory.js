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

	/**
	 * Registry for the builder to register themselfes.
	 */
	this.builder = {};
	
	this._register(new Bestia.Engine.MultispriteBuilder());
	this._register(new Bestia.Engine.SimpleObjectBuilder());
	this._register(new Bestia.Engine.ItemBuilder(game, demandLoader));
};

Bestia.Engine.EntityFactory.prototype._register = function(b) {
	this.builder[b.type] = b;
};

Bestia.Engine.EntityFactory.prototype.build = function(data) {
	var type = '';
	
	switch (data.t) {
	case 'ITEM':
		// let an item appear.
		type = 'item';
		break;
	case 'STATIC':
		// Static sprite appear.
		type = 'static';
		break;
	case 'MOB_ANIM':
		// Normal bestia.
		
		break;
	case 'PLAYER_ANIM':
		// Player animation sprite.
		type = 'multisprite';
		break;
	}
	
	var b = this.builder[data.type];
	var entity = null;
	
	if(b === undefined) {
		return entity;
	} 
	
	entity = b.build(data);
	entity.addToGroup(self._groups.sprites);
	this._entityCache.addEntity(entity);
	
	return entity;
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

Bestia.Engine.EntityFactory.prototype.createPlayerEntity = function() {

	// var self = this;

	console.error("Not implemented");

};
