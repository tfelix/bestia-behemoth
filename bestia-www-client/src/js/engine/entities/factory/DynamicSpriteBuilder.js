import SpriteBuilder from './SpriteBuilder';
import MultispriteEntity from '../MultispriteEntity';
import LOG from '../../../util/Log';
import groups, {GROUP_LAYERS} from '../../Groups';
import { engineContext } from '../../EngineData';

/**
 * This is able to create sprite entities which differ to the runtime. It must
 * react automatically when created to data delivered by the server. A player
 * sprite is a good example: the hair type is only known to the server at
 * runtime. The sprite will be created based upon this data.
 */
export default class DynamicSpriteBuilder extends SpriteBuilder {
	
	constructor(factory) {
		super(factory);
		
		// Register with factory.
		this.type = 'dynamic';
		this.version = 1;
		
		this._pubsub = engineContext.pubsub;
	}
	
	build(data, desc) {
		LOG.debug('Building dynamic sprite.', data);
		
		var entity = new MultispriteEntity(data.eid, data.position.x, data.position.y, desc);

		// Setup the phaser sprite.
		entity.setSprite(data.sprite.name);
		
		// Set the apropriate callbacks for the (player) entities.
		/*
		entity.onInputOver = function() {
			this._pubsub.publish(Signal.ENGINE_REQUEST_INDICATOR, {handle: 'basic_attack_over', entity: entity});
		}.bind(this);
		
		entity.onInputOut = function() {
			this._pubsub.publish(Signal.ENGINE_REQUEST_INDICATOR, {handle: 'basic_attack_out', entity: entity});
		}.bind(this);*/
		
		groups.get(GROUP_LAYERS.SPRITES).add(entity._sprite);

		if (data.action === 'APPEAR') {
			entity.appear();
		} else {
			entity.show();
		}

		entity.setPosition(data.position.x, data.position.y);
		
		entity.entityId = data.eid;
			
		return entity;
	}

	/**
	 * The type of the entities does now not match the sane check. It must be
	 * corrected.
	 */
	canBuild(data) {
		return data.sprite.type === 'DYNAMIC';
	}

}