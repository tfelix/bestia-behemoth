import PhaserSpriteCache from './PhaserSpriteCache';
import Pathfinder from './map/Pathfinder';
import SpriteDescriptionCache from './entities/SpriteDescriptionCache';
import EntityCacheEx from './entities/EntityCacheEx';
import EntityComponentUpdater from './entities/EntityComponentUpdater';


/**
 * Contains the data for the phaser engine to render. This data is accessed
 * in a global and static way.
 */
export default class EngineContext {

	constructor(pubsub, url) {

		this.pubsub = pubsub;
		this.url = url;

		this.entityCache = new EntityCacheEx();
		this.entityUpdater = new EntityComponentUpdater(pubsub, this.entityCache);

		this.spriteCache = new PhaserSpriteCache();
		this.pathfinder = new Pathfinder();
		this.descriptionCache = new SpriteDescriptionCache();
		
		this.game = null;
		this.renderManager = null;
		this.indicatorManager = null;
		this.loader = null;
		this.data = {};
	}
}