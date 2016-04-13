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
	this.builder = [];

	//this.builder.push(new Bestia.Engine.MultispriteBuilder(this, ctx));
	this.builder.push(new Bestia.Engine.PlayerMultispriteBuilder(this, ctx));
	this.builder.push(new Bestia.Engine.SpriteBuilder(this, ctx));
	this.builder.push(new Bestia.Engine.SimpleObjectBuilder(this, ctx));
	this.builder.push(new Bestia.Engine.ItemBuilder(this, ctx));
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
			
			if (!b) {
				console.warn("Could not build entity. From data: " + JSON.stringify(data));
				return;
			}
			
			b.load(descFile, function() {
				
				if (descFile === null) {
					// Could not load desc file.
					return;
				}

				var entity = b.build(data, descFile);

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
	for(var i = 0; i < this.builder.length; i++) {
		if(this.builder[i].canBuild(data, descFile)) {
			return this.builder[i];
		}
	}
	
	return null;
};
