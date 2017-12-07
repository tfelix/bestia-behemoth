import PhaserSpriteCache from './PhaserSpriteCache';
import EntityCacheEx from './entities/EntityCacheEx';
import Pathfinder from './map/Pathfinder';
import SpriteDescriptionCache from './entities/SpriteDescriptionCache';

/**
 * Contains the data for the phaser engine to render. This data is accessed
 * in a global and static way.
 */

var spriteCache = new PhaserSpriteCache();
var pathfinder = new Pathfinder();
var descriptionCache = new SpriteDescriptionCache();

/**
 * The engine context ist setup inside the initialize state.
 */
var engineContext = {
	pubsub: null,
	renderManager: null,
	indicatorManager: null,
	loader: null,
	url: null,
	data: {}
};

export {
	spriteCache,
	engineContext,
	pathfinder,
	descriptionCache
};