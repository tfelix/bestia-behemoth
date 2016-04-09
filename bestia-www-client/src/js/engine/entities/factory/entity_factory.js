/**
 * The factory is responsible for loading all the needed assets to display a
 * certain entity. It resolves if it is a bestia, sprite, item etc. entity and
 * uses the correct javascript class to manage it. It gets added to the entity
 * cache to receive updates.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 */
Bestia.Engine.EntityFactory = function(game, loader, entityCache, groups, urlHelper) {

	this._game = game;

	this.loader = loader;
	this.descLoader = new Bestia.Engine.DescriptionLoader(loader, urlHelper);

	this.groups = groups;

	this._entityCache = entityCache;
	
	/**
	 * Registry for the builder to register themselfes.
	 */
	this.builder = {};

	this._register(new Bestia.Engine.MultispriteBuilder(this));
	this._register(new Bestia.Engine.SpriteBuilder(this));
	this._register(new Bestia.Engine.SimpleObjectBuilder(this));
	this._register(new Bestia.Engine.ItemBuilder(game, loader));
};

Bestia.Engine.EntityFactory.prototype._register = function(b) {
	this.builder[b.type] = b;
};

Bestia.Engine.EntityFactory.prototype.build = function(data, fnOnComplete) {
	fnOnComplete = fnOnComplete || function() { };

	// Do we already have the desc file?
	var descFile = this.descLoader.getDescription(data);

	if (descFile === null) {
		// We must first load this file because we dont know anything about the
		// entity. Hand over the now loaded description file aswell as the callback.
		this.descLoader.loadDescription(data, function(descFile){
			
			var pack = descFile.assetpack;

			this.loader.loadPackData(pack, function() {
				this._build(data, descFile, fnOnComplete);
			}.bind(this));
			
		}.bind(this));
	} else {
		this._build(descFile, fnOnComplete);
	}
};

Bestia.Engine.EntityFactory.prototype._build = function(data, descFile, fnOnComplete) {
	
	if(descFile === null) {
		// Could not load desc file.
		return;
	}

	var b = this.builder[descFile.type];

	if (b === undefined) {
		return null;
	}

	var entity = b.build(data, descFile);

	if (entity === null) {
		console.warn("Could not build entity. From data: " + JSON.stringify(data));
		return;
	}

	entity.addToGroup(self._groups.sprites);
	this._entityCache.addEntity(entity);

	// Call the callback handler.
	fnOnComplete(entity);
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