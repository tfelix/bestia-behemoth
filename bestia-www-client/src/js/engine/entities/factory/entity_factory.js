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
	this._register(new Bestia.Engine.SpriteBuilder());
	this._register(new Bestia.Engine.SimpleObjectBuilder());
	this._register(new Bestia.Engine.ItemBuilder(game, demandLoader));
};

Bestia.Engine.EntityFactory.prototype._register = function(b) {
	this.builder[b.type] = b;
};

Bestia.Engine.EntityFactory.prototype.build = function(data) {
	var type = data.type || this._getType(data);

	var b = this.builder[type];
	var entity = null;

	if (b === undefined) {
		return entity;
	}

	entity = b.build(data);
	
	if(entity === null) {
		console.warn("Could not build entity. From data: " + JSON.stringify(data));
		return;
	}
	
	entity.addToGroup(self._groups.sprites);
	this._entityCache.addEntity(entity);

	return entity;
};

Bestia.Engine.EntityFactory.prototype._getType = function(data) {
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
		type = 'sprite';
		break;
	case 'PLAYER_ANIM':
		// Player animation sprite.
		type = 'multisprite';
		break;
	default:
		// no op.
		break;
	}

	return type;
};