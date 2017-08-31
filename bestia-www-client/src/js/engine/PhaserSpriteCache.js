
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
        this._spriteCache[eid] = sprite;
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