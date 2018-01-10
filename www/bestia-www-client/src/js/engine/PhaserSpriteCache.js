import LOG from '../util/Log';

/**
 * Contains a cache holding references to display objects and entity ids which must
 * be used to reference them vice versa.
 */
export default class PhaserSpriteCache {

    constructor() {

        this.clear();
    }

    clear() {
        this._spriteCache = {};
    }

    getSprite(eid) {
        return this._spriteCache[eid];
    }

    setSprite(eid, sprite) {
        LOG.debug('Adding sprite for eid: ' + eid + ' spriteKey: ' + sprite.key);
        this._spriteCache[eid] = sprite;
    }
}