/**
 * Responsible for building the multisprites entities.
 */
Bestia.Engine.SpriteBuilder = function(factory) {
	
	
	// Register with factory.
	this.type = 'sprite';
	this.version = 1;

};

Bestia.Engine.SpriteBuilder.prototype = Object.create(Bestia.Engine.Builder.prototype);
Bestia.Engine.SpriteBuilder.prototype.constructor = Bestia.Engine.SpriteBuilder;

Bestia.Engine.SpriteBuilder.prototype._build = function(data) {
	
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