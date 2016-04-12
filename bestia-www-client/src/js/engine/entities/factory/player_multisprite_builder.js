/**
 * This will modify the multisprite entity so it matches an player entity.
 */
Bestia.Engine.PlayerMultispriteBuilder = function(factory, ctx) {
	Bestia.Engine.MultispriteBuilder.call(this, factory, ctx);

	// Register with factory.
	this.type = 'playermultisprite';
	this.version = 1;
};

Bestia.Engine.PlayerMultispriteBuilder.prototype = Object.create(Bestia.Engine.MultispriteBuilder.prototype);
Bestia.Engine.PlayerMultispriteBuilder.prototype.constructor = Bestia.Engine.PlayerMultispriteBuilder;

Bestia.Engine.PlayerMultispriteBuilder.prototype._build = function(data, desc) {
	var entity = Bestia.Engine.MultispriteBuilder.prototype._build.call(this, data, desc);
	
	entity.playerBestiaId = data.pbid;
		
	return entity;
};

/**
 * The type of the entities does now not match the sane check. It must be corrected.
 */
Bestia.Engine.Builder.prototype._isSane = function(data) {
	return data.type === 'multisprite' && data.version === this.version;
};
