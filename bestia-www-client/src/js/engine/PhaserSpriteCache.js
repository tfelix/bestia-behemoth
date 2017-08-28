
/**
 * Contains a cache holding references to display objects and entity ids which must
 * be used to reference them vice versa.
 */

class PhaserSpriteCache {

    constructor() {

        this.clear();
    }

    clear() {
        this._spriteCache = {};
        this._playerSprite = null;
    }

    getSprite(eid) {
        return this._spriteCache[eid];
    }

    setSprite(eid, sprite) {
        this._spriteCache[eid] = sprite;
    }

    getPlayerSprite() {
        return this._playerSprite;
    }

    setPlayerSprite(sprite) {
        this._playerSprite = sprite;
    }
}

var cache = new PhaserSpriteCache();

export {cache as default};