import Builder from './Builder.js';

/**
 * Responsible for building items on the map.
 */
export default class ItemBuilder extends Builder {
	constructor(factory, ctx) {
		super(factory, ctx);
		
		
		// Register with factory.
		this.type = 'item';
		this.version = 1;
		
		this._loader = ctx.loader;
		this._game = ctx.game;
	}
	
	build(data) {
		
		var entity;
		
		if(this._loader.has(data.s, 'item')) {
			entity = new Bestia.Engine.ItemEntity(this._ctx, data.uuid, data.x, data.y, data.s);
		} else {
			entity = new Bestia.Engine.ItemEntity(this._ctx, data.uuid, data.x, data.y);
			
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
	}

	canBuild(data) {
		return data.type === this.type && data.version === this.version;
	}
}