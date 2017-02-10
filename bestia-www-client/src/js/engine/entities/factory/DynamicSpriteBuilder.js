import MultispriteBuilder from './MultispriteBuilder';
import MultispriteEntity from '../MultispriteEntity';
import LOG from '../../../util/Log';

/**
 * This is able to create sprite entities which differ to the runtime. It must
 * react automatically when created to data delivered by the server. A player
 * sprite is a good example: the hair type is only known to the server at
 * runtime. The sprite will be created based upon this data.
 */
export default class DynamicSpriteBuilder extends MultispriteBuilder {
	
	constructor(factory, ctx) {
		super(factory, ctx);
		
		// Register with factory.
		this.type = 'dynamic';
		this.version = 1;
		
		this._pubsub = ctx.pubsub;
	}
	
	build(data, desc) {
		LOG.debug('Building dynamic sprite.', data);
		
		var entity = new MultispriteEntity(this._ctx, data.eid, data.x, data.y, desc);

		// Setup the phaser sprite.
		entity.setSprite(data.s.s);
		
		// Set the callbacks.
		entity.onInputOver = function() {
			this._pubsub.publish(Signal.ENGINE_REQUEST_INDICATOR, {handle: 'basic_attack_over', entity: entity});
		}
		entity.onInputOut = function() {
			this._pubsub.publish(Signal.ENGINE_REQUEST_INDICATOR, {handle: 'basic_attack_out', entity: entity});
		}
		
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