import Builder from './Builder.js';
import groups, { GROUP_LAYERS } from '../../Groups';
import LOG from '../../../util/Log';
import Signal from '../../../io/Signal';
import { engineContext } from '../../EngineData';
import { setupSpriteAnimation } from '../traits/VisualTrait';
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
		var pos = WorldHelper.getPxXY(data.position.x + 0.5, data.position.y + 0.9);
		var sprite = this._game.add.sprite(pos.x, pos.y, desc.name);

		groups.get(GROUP_LAYERS.SPRITES).add(sprite);

		setupSpriteAnimation(sprite, desc);

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