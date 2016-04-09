/**
 * Responsible for building the multisprites entities.
 */
Bestia.Engine.MultispriteBuilder = function(game, loader) {
	
	
	// Register with factory.
	this.type = 'multisprite';
	this.version = 1;
	
	this._loader = loader;
	this._game = game;
};

Bestia.Engine.MultispriteBuilder.prototype = Object.create(Bestia.Engine.Builder.prototype);
Bestia.Engine.MultispriteBuilder.prototype.constructor = Bestia.Engine.MultispriteBuilder;

Bestia.Engine.MultispriteBuilder.prototype._build = function(data) {
	
	var entity = new Bestia.Engine.MultispriteEntity(this._game, data.uuid, data.x, data.y, data);

	this._loader.loadMobSprite(data.s, function() {

		entity.setSprite(data.s);
		//entity.addToGroup(this._groups.sprites);

		if (data.a === "APPEAR") {
			entity.appear();
		} else {
			entity.show();
		}
	}.bind(this));
	

};