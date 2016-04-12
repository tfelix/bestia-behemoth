/**
 * Responsible for building the multisprites entities.
 */
Bestia.Engine.SpriteBuilder = function(factory, ctx) {
	Bestia.Engine.Builder.call(this, factory, ctx);
	
	
	// Register with factory.
	this.type = 'sprite';
	this.version = 1;


};

Bestia.Engine.SpriteBuilder.prototype = Object.create(Bestia.Engine.Builder.prototype);
Bestia.Engine.SpriteBuilder.prototype.constructor = Bestia.Engine.SpriteBuilder;

Bestia.Engine.SpriteBuilder.prototype._build = function(data, desc) {
	

	var entity = new Bestia.Engine.SpriteEntity(this._ctx.game, data.uuid, data.x, data.y, desc);

	entity.setSprite(data.s);
	
	entity.addToGroup(this._ctx.groups.sprites);

	if (data.a === "APPEAR") {
		entity.appear();
	} else {
		entity.show();
	}

	return entity;
};