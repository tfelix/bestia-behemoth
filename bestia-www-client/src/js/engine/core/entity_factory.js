
Bestia.Engine.EntityFactory = function(game, demandLoader, entityCache) {

	this._game = game;
	
	this._demandLoader = demandLoader;
	
	this._entityCache = entityCache;
	
};

Bestia.Engine.EntityFactory.prototype.createBestiaEntity = function(data) {
	
	console.log("### CREATE BESTIA ###");
	console.log(data);
	
	
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
		var itemEntity = Bestia.Engine.EntityItem(uuid, x, y, spriteName);
		
		self._entityCache.addEntity(itemEntity);
	});
	
	
};
