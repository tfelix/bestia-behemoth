import Builder from './Builder.js';
import groups, { GROUP_LAYERS } from '../../Groups';
import LOG from '../../../util/Log';
import Signal from '../../../io/Signal';
import { engineContext } from '../../EngineData';
import { setupSpriteAnimation } from '../SpriteAnimationHelper';
import WorldHelper from '../../map/WorldHelper';

/**
 * Responsible for building the packed (multisprite) entities. These are usually
 * mobs. All sprites which consists of multiple sprites with animation data.
 */
export default class SpriteBuilder extends Builder {
	constructor(game) {
		super();

		this._game = game;

		// Register with factory.
		this.version = 1;
	}

	build(data, desc) {
		LOG.debug('Building: ' + JSON.stringify(data) + ' (sprite)');

		// Create the sprite.
		var pos = WorldHelper.getPxXY(data.position.x + 0.5, data.position.y + 0.5);
		var sprite = this._game.add.sprite(pos.x, pos.y, desc.name);

		groups.get(GROUP_LAYERS.SPRITES).add(sprite);

		setupSpriteAnimation(sprite, desc);

		// We need to check how we can interact with this entity. If this is
		// clear we add the behaviour of the move over. Currently its attack
		// only.
		/*
		entity.onInputOver = function () {
			this._pubsub.publish(Signal.ENGINE_REQUEST_INDICATOR, { handle: 'basic_attack_over', entity: entity });
		}.bind(this);

		entity.onInputOut = function () {
			this._pubsub.publish(Signal.ENGINE_REQUEST_INDICATOR, { handle: 'basic_attack_out', entity: entity });
		}.bind(this);*/


		// Switch to idle animation.
		sprite.animations.play('stand_up');

		return sprite;
	}

			/**
	 * Loads all needed assets in order to perform a visual build of this
	 * entity. The fnOnComplete is called when all data has been loaded.
	 */
	load(descFile, fnOnComplete) {	
		var pack = descFile.assetpack;
		engineContext.loader.loadPackData(pack, fnOnComplete);
	}

	canBuild(data) {
		return data.sprite.type.toUpperCase() === 'PACK';
	}
}