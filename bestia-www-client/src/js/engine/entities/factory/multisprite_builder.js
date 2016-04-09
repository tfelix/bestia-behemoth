/**
 * Responsible for building the multisprites entities.
 */
Bestia.Engine.MultispriteBuilder = function(factory) {
	
	
	// Register with factory.
	this.type = 'multisprite';
	this.version = 1;
	
	this._factory = factory;
};

Bestia.Engine.MultispriteBuilder.prototype = Object.create(Bestia.Engine.Builder.prototype);
Bestia.Engine.MultispriteBuilder.prototype.constructor = Bestia.Engine.MultispriteBuilder;

Bestia.Engine.MultispriteBuilder.prototype._build = function(data, desc) {
	
	var entity = new Bestia.Engine.MultispriteEntity(this._factory.game, data.uuid, desc);
	
	// Position
	entity.setPosition(data.x, data.y);
	
	entity.setSprite(data.s);

	if (data.a === "APPEAR") {
		entity.appear();
	} else {
		entity.show();
	}
	
	return entity;
};