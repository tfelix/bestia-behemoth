/**
 * Responsible for building the entities consisting of single sprites.
 */
Bestia.Engine.SpriteBuilder = function(factory, ctx) {
	Bestia.Engine.Builder.call(this, factory, ctx);

	// Register with factory.
	this.version = 1;

};

Bestia.Engine.SpriteBuilder.prototype = Object.create(Bestia.Engine.Builder.prototype);
Bestia.Engine.SpriteBuilder.prototype.constructor = Bestia.Engine.SpriteBuilder;

Bestia.Engine.SpriteBuilder.prototype.build = function(data, desc) {

	var entity = new Bestia.Engine.ImageEntity(this._ctx, data.uuid, data.x, data.y, desc);

	entity.setSprite(data.s);

	entity.addToGroup(this._ctx.groups.sprites);

	if (data.a === "APPEAR") {
		entity.appear();
	} else {
		entity.show();
	}

	return entity;
};

Bestia.Engine.SpriteBuilder.prototype.load = function(descFile, fnOnComplete) {
	var pack = {};
	pack[descFile.name] = [ {
		type : "image",
		key : descFile.name,
		url : this._ctx.url.getSpriteUrl(descFile.name)
	} ]
	this._ctx.loader.loadPackData(pack, fnOnComplete);
};

Bestia.Engine.SpriteBuilder.prototype.canBuild = function(data) {
	return data.t === 'STATIC';
};