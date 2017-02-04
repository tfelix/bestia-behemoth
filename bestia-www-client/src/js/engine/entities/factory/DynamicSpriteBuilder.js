import MultispriteBuilder from './MultispriteBuilder';
import MultispriteEntity from '../MultispriteEntity';
import LOG from '../../../util/Log';

/**
 * This is able to create sprite entities which differ to the runtime. It must
 * react automatically when created to data described inside its description
 * file.
 */
export default class DynamicSpriteBuilder extends MultispriteBuilder {
	
	constructor(factory, ctx) {
		super(factory, ctx);
		
		// Register with factory.
		this.type = 'dynamic';
		this.version = 1;
	}
	
	build(data, desc) {
		LOG.debug('Building dynamic sprite.', data);
		
		if(data.onlyLoad) {
			alert("wtf");
			return null;
		}
		
		var entity = new MultispriteEntity(this._ctx, data.eid, data.x, data.y, desc);

		// Setup the phaser sprite.
		entity.setSprite(data.s.s);
		
		entity.addToGroup(this._ctx.groups.sprites);

		if (data.a === 'APPEAR') {
			entity.appear();
		} else {
			entity.show();
		}
		
		entity.playerBestiaId = data.pbid;
			
		return entity;
	}

	/**
	 * The type of the entities does now not match the sane check. It must be
	 * corrected.
	 */
	canBuild(data) {
		return data.s.t === 'DYNAMIC';
	}

}