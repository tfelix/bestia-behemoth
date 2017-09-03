/**
 * Contains the different layer groups so the engine can draw the layer into correct z-order.
 */

const GROUP_LAYERS = Object.freeze({
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
        this._groups[GROUP_LAYERS.TILES] = game.add.group(undefined, 'tiles');
        this._groups[GROUP_LAYERS.SPRITES_BOTTOM] = game.add.group(undefined, 'sprites_under');
		this._groups[GROUP_LAYERS.SPRITES] = game.add.group(undefined, 'sprites');
		this._groups[GROUP_LAYERS.SPRITES_TOP] =game.add.group(undefined, 'sprites_over');
		this._groups[GROUP_LAYERS.FX] = game.add.group(undefined, 'fx');
		this._groups[GROUP_LAYERS.MAP_OVERLAY] = game.add.group(undefined, 'map_overlay');
		this._groups[GROUP_LAYERS.GUI] = game.add.group(undefined, 'gui');
    }

    sort(layer) {
        this.get(layer).sort('y', Phaser.Group.SORT_ASCENDING);
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
groupManager.LAYERS = GROUP_LAYERS;

export { groupManager as default, GROUP_LAYERS };

