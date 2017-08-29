
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

/*
var entityData = {
					eid: msg.eid,
					sprite: { name: msg.s.s, type: msg.s.t },
                    position: { x: msg.x, y: msg.y },
                    movement: { path: [], speed: 0, delta: 0 }
                    action: remove || appear
                    animation: null
				}

*/

var cache = new PhaserSpriteCache();

export {cache as default};