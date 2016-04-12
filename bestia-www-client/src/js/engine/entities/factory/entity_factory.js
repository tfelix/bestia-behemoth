/**
 * The factory is responsible for loading all the needed assets to display a
 * certain entity. It resolves if it is a bestia, sprite, item etc. entity and
 * uses the correct javascript class to manage it. It gets added to the entity
 * cache to receive updates.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 */
Bestia.Engine.EntityFactory = function(ctx) {
	
	if(!ctx) {
		throw new Error("Context can not be null.");
	}
	
	this._ctx = ctx;
	
	this.descLoader = new Bestia.Engine.DescriptionLoader(ctx.loader, ctx.url);

	/**
	 * Registry for the builder to register themselfes.
	 */
	this.builder = {};

	this._register(new Bestia.Engine.MultispriteBuilder(this, ctx));
	this._register(new Bestia.Engine.PlayerMultispriteBuilder(this, ctx));
	this._register(new Bestia.Engine.SpriteBuilder(this, ctx));
	this._register(new Bestia.Engine.SimpleObjectBuilder(this, ctx));
	this._register(new Bestia.Engine.ItemBuilder(this, ctx));
};

Bestia.Engine.EntityFactory.prototype._register = function(b) {
	this.builder[b.type] = b;
};

Bestia.Engine.EntityFactory.prototype.build = function(data, fnOnComplete) {
	fnOnComplete = fnOnComplete || Bestia.NOOP;

	// Do we already have the desc file?
	var descFile = this.descLoader.getDescription(data);

	if (descFile === null) {
		// We must first load this file because we dont know anything about the
		// entity. Hand over the now loaded description file as well as the
		// callback.
		this.descLoader.loadDescription(data, function(descFile) {

			var b = this._getBuilder(data, descFile);
			
			b.load(descFile, function() {
				
				if (descFile === null) {
					// Could not load desc file.
					return;
				}

				var entity = b.build(data, descFile);

				if (!entity) {
					console.warn("Could not build entity. From data: " + JSON.stringify(data));
					return;
				}

				this._ctx.entityCache.addEntity(entity);

				// Call the callback handler.
				fnOnComplete(entity);
				
				
			}.bind(this));

		}.bind(this));
	} else {
		this._build(descFile, fnOnComplete);
	}
};

/**
 * Das müsste auch an die Builder ausgelagert werden.
 */
Bestia.Engine.EntityFactory.prototype._getBuilder = function(data, descFile) {
	if(data.t === 'PLAYER_ANIM' && data.pbid) {
		return this.builder['playermultisprite'];
	}
	return this.builder[descFile.type];
};


Bestia.Engine.EntityFactory.prototype._getType = function(data) {
	// TODO Das hier durch die Builder automatisieren.
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
		type = 'playermultisprite';
		break;
	default:
		// no op.
		break;
	}

	return type;
};