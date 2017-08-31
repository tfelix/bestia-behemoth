import PhaserSpriteCache from './PhaserSpriteCache';
import EntityCacheEx from './entities/EntityCacheEx';
import Pathfinder from './map/Pathfinder';

/**
 * Contains the data for the phaser engine to render.
 */

var spriteCache = new PhaserSpriteCache();
var entityCache = new EntityCacheEx();
var pathfinder = new Pathfinder(); 

var engineContext = {
    pubsub: null,
    renderManager: null,
    demandLoader: null,
    indicatorManager: null,
    loader: null,
    url: null,
    entityUpdater: null
};

export {spriteCache, entityCache, engineContext, pathfinder};