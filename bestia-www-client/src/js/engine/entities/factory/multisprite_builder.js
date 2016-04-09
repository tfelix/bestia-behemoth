/**
 * Responsible for building the multisprites entities.
 */
Bestia.Engine.MultispriteBuilder = function(factory) {
	
	
	// Register with factory.
	this.type = 'multisprite';
	this.version = 1;

};

Bestia.Engine.MultispriteBuilder.prototype.build = function(data) {
	
	var self = this;
	var entity = new Bestia.Engine.MultispriteEntity(self._game, data.uuid, data.x, data.y, data);

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