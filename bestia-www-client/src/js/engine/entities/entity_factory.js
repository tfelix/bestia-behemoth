
Bestia.Engine.EntityFactory = function(game, demandLoader, entityCache) {

	this._game = game;
	
	this._demandLoader = demandLoader;
	
	this._entityCache = entityCache;
	
};

Bestia.Engine.EntityFactory.prototype.createBestiaEntity = function(data) {
	
	var self = this;
	var entity = new Bestia.Engine.SpriteEntity(self._game, data.uuid, data.x, data.y, data.pbid);
	self._entityCache.addEntity(entity);
	
	this._demandLoader.loadMobSprite(data.s, function(){
		
		entity.setSprite(data.s);
		
		if(data.a === "APPEAR") {
			entity.appear();
		} else {
			entity.show();
		}		
	});
	
	
};


Bestia.Engine.EntityFactory.prototype.createItemEntity = function(data) {
	
	var self = this;

	this._demandLoader.loadItemSprite(data.s, function(){
		
		var entity = new Bestia.Engine.ItemEntity(self._game, data.uuid, data.x, data.y, data.s);
		self._entityCache.addEntity(entity);
		
		if(data.a === "APPEAR") {
			entity.appear();
		} else {
			entity.show();
		}
	});
	
	
};
