
Bestia.Engine.EntityFactory = function(game, demandLoader, entityCache) {

	this._game = game;
	
	this._demandLoader = demandLoader;
	
	this._entityCache = entityCache;
	
};

Bestia.Engine.EntityFactory.prototype.createBestiaEntity = function(data) {
	
	console.trace("### CREATE BESTIA ###");
	
	var self = this;
	
	this._demandLoader.loadMobSprite(data.s, function(){

		var entity = new Bestia.Engine.SpriteEntity(self._game, data.uuid, data.x, data.y, data.s);		
		self._entityCache.addEntity(entity);
		entity.appear();
		
	});
	
	
};


Bestia.Engine.EntityFactory.prototype.createItemEntity = function(data) {
	
	console.log("### CREATE ITEM ###");
	console.log(data);
	
	var self = this;
	
	var spriteName = "apple";
	var uuid = "123";
	
	this._demandLoader.loadItemSprite(spriteName, function(){
		var x = 10;
		var y = 10;
		var itemEntity = new Bestia.Engine.EntityItem(uuid, x, y, spriteName);
		
		self._entityCache.addEntity(itemEntity);
	});
	
	
};
