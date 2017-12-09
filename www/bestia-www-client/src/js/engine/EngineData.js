import PhaserSpriteCache from './PhaserSpriteCache';
import Pathfinder from './map/Pathfinder';
import SpriteDescriptionCache from './entities/SpriteDescriptionCache';

/**
 * Contains the data for the phaser engine to render. This data is accessed
 * in a global and static way.
 */
export default class EngineData {

	constructor(pubsub, url) {

		this.spriteCache = new PhaserSpriteCache();
		this.pathfinder = new Pathfinder();
		this.descriptionCache = new SpriteDescriptionCache();

		this.pubsub = pubsub;
		this.url = url;
		
		this.renderManager = null;
		this.indicatorManager = null;
		this.loader = null;
		this.data = {};
	}
}