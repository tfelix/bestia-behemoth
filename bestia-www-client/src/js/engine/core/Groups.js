/**
 * Contains the different layer groups so the engine can draw the layer into correct z-order.
 */

const GroupLayers = Object.freeze({
    TILES: 0,
    SPRITES_BOTTOM: 1,
    SPRITES: 2,
    SPRITES_TOP: 3,
    FX: 4,
    MAP_OVERLAY: 5,
    GUI: 6
});

class GroupManager {

    constructor() {
        this._groups = [];
    }

    /**
     * Initializes a new group layer system.
     * @param {PhaserGame} game PhaserJS game object.
     */
    initilize(game) {
        this._groups = [];
        this._groups[GroupLayers.TILES] = this.game.add.group(undefined, 'tiles');
        this._groups[GroupLayers.SPRITES_BOTTOM] = this.game.add.group(undefined, 'sprites_under');
		this._groups[GroupLayers.SPRITES] = this.game.add.group(undefined, 'sprites');
		this._groups[GroupLayers.SPRITES_TOP] = this.game.add.group(undefined, 'sprites_over');
		this._groups[GroupLayers.FX] = this.game.add.group(undefined, 'fx');
		this._groups[GroupLayers.MAP_OVERLAY] = this.game.add.group(undefined, 'map_overlay');
		this._groups[GroupLayers.GUI] = this.game.add.group(undefined, 'gui');
    }

    /**
     * Returns the layer of the group.
     * @param {number} layer The layer of the group to return.
     */
    get(layer) {

        if(layer < 0 || layer > this._groups.length) {
            throw 'Layer is not present.';
        }

        return this._groups[layer];
    }
}

var groupManager = new GroupManager();

export { groupManager as default, GroupLayers };

