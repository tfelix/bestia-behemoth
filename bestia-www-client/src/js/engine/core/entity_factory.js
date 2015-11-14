
Bestia.Engine.EntityFactory = function(game, demandLoader, entityCache) {

	this._game = game;
	
	this._demandLoader = demandLoader;
	
	this._entityCache = entityCache;
	
};

Bestia.Engine.EntityFactory.prototype.createBestiaEntity = function(data) {
	
	var self = this;
	
	this._demandLoader.loadMobSprite(data.s, function(){

		var entity = new Bestia.Engine.SpriteEntity(self._game, data.uuid, data.x, data.y, data.s, data.pbid);		
		self._entityCache.addEntity(entity);
		entity.appear();
		
	});
	
	
};


Bestia.Engine.EntityFactory.prototype.createItemEntity = function(data) {
	
	console.log("### CREATE ITEM ###");
	console.log(data);
	
	var self = this;
	
	var key = "empty_bottle";
	var spriteName = "empty_bottle.png";

	this._demandLoader.loadItemSprite(key, spriteName, function(){
		
		var entity = new Bestia.Engine.ItemEntity(self._game, data.uuid, data.x, data.y, key);
		self._entityCache.addEntity(entity);
		entity.appear();
	});
	
	
};
