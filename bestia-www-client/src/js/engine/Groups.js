/**
 * Contains the different layer groups so the engine can draw the layers 
 * and thus the entities into correct z-order.
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

        /**
         * The bottom layer of the tile map.
         */
        this._groups[GROUP_LAYERS.TILES] = game.add.group(undefined, 'tiles');

        /**
         * The layer below of each entity sprite in the game.
         */
        this._groups[GROUP_LAYERS.SPRITES_BOTTOM] = game.add.group(undefined, 'sprites_under');

        /**
         * Mainlayer of all entity sprites in the game.
         */
        this._groups[GROUP_LAYERS.SPRITES] = game.add.group(undefined, 'sprites');
        
        /**
         * Top layer over the sprites in the game.
         */
        this._groups[GROUP_LAYERS.SPRITES_TOP] =game.add.group(undefined, 'sprites_over');
        
        /**
         * Effect layer on top of each sprite and sprite overlay.
         */
        this._groups[GROUP_LAYERS.FX] = game.add.group(undefined, 'fx');
        
        /**
         * A map overlay layer. Useful for text, names etc.
         */
        this._groups[GROUP_LAYERS.MAP_OVERLAY] = game.add.group(undefined, 'map_overlay');
        
        /**
         * The outermost GUI layer. Is always visible and never overlayed.
         */
		this._groups[GROUP_LAYERS.GUI] = game.add.group(undefined, 'gui');
    }

    /**
     * Sorts all the sprites inside the given layer id in a Z-ascending order. So they overlay
     * in the correct oder.
     */
    sort(layerId) {
        this.get(layerId).sort('y', Phaser.Group.SORT_ASCENDING);
    }

    /**
     * Returns the layer of the group.
     * @param {number} layer The layer of the group to return.
     */
    get(layer) {

        if(layer < 0 || layer > this._groups.length) {
            throw 'Group#get: Layer id '+ layer +' is not present.';
        }

        return this._groups[layer];
    }
}

var groupManager = new GroupManager();
groupManager.LAYERS = GROUP_LAYERS;

export { groupManager as default, GROUP_LAYERS };

