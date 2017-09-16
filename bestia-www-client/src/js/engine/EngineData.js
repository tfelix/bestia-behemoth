import PhaserSpriteCache from './PhaserSpriteCache';
import EntityCacheEx from './entities/EntityCacheEx';
import Pathfinder from './map/Pathfinder';
import SpriteDescriptionCache from './entities/SpriteDescriptionCache';

/**
 * Contains the data for the phaser engine to render.
 */

var spriteCache = new PhaserSpriteCache();
var entityCache = new EntityCacheEx();
var pathfinder = new Pathfinder();
var descriptionCache = new SpriteDescriptionCache();

var engineContext = {
    pubsub: null,
    renderManager: null,
    indicatorManager: null,
    loader: null,
    url: null,
    entityUpdater: null,
    data : {}
};

export {
    spriteCache,
    entityCache,
    engineContext,
    pathfinder,
    descriptionCache
};