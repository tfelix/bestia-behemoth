/**
 * Responsible for building the multisprites entities.
 */
Bestia.Engine.ItemBuilder = function(game, loader) {
	
	
	// Register with factory.
	this.type = 'item';
	this.version = 1;
	
	this._loader = loader;
	this._game = game;

};

Bestia.Engine.ItemBuilder.prototype = Object.create(Bestia.Engine.Builder.prototype);
Bestia.Engine.ItemBuilder.prototype.constructor = Bestia.Engine.ItemBuilder;

Bestia.Engine.ItemBuilder.prototype._build = function(data) {
	
	var entity;
	
	if(this._loader.has(data.s, 'item')) {
		entity = new Bestia.Engine.ItemEntity(this._game, data.uuid, data.x, data.y, data.s);
	} else {
		entity = new Bestia.Engine.ItemEntity(this._game, data.uuid, data.x, data.y);
		
		this._demandLoader.loadItemSprite(data.s, function() {

			entity.setTexture(data.s);

		}.bind(this));
	}
	
	if (data.a === "APPEAR") {
		entity.appear();
	} else {
		entity.show();
	}
	
	return entity;
};