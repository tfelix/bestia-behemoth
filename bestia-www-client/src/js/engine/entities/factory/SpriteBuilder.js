import Builder from './Builder.js';
import ImageEntity from '../ImageEntity.js';

/**
 * Responsible for building the entities consisting of single sprites (NPC,
 * trees, houses, decals etc.).
 */

export default class SpriteBuilder extends Builder {
	constructor(factory, ctx) {
		super(factory, ctx);
		
		// Register with factory.
		this.version = 1;
	}
	
	build(data, desc) {
		if(data.onlyLoad) {
			return null;
		}
		
		var entity = new ImageEntity(this._ctx, data.uuid, data.x, data.y, desc);

		entity.setSprite(data.s);

		entity.addToGroup(this._ctx.groups.sprites);

		if (data.a === 'APPEAR') {
			entity.appear();
		} else {
			entity.show();
		}

		return entity;
	}

	load(descFile, fnOnComplete) {

		var data = {
			key : 'emperium',
			type : 'image',
			url : this._ctx.url.getSpriteUrl(descFile.name)
		};
		this._ctx.loader.load(data, fnOnComplete);
	}

	canBuild(data) {
		return data.t === 'STATIC';
	}
}