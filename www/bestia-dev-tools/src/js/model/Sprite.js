import 'knockout';

/**
 * Basic data model for a bestia sprite. It contains all the needed information
 * to render a sprite entity. It is basically more then a sprite it contains all
 * the data and information associated with it. (animations, sub-sprites, anchors etc.)
 */
export default class Sprite {

    /**
     * Creates an instance of Sprite.
     * @memberof Sprite
     */
    constructor() {

        this.name = ko.observable('');
        this.type = ko.observable('');
        this.version = ko.observable(0);
        this.scale = ko.observable(1);
        this.animations = ko.observableArray();
        this.anchor;
        this.multiSprite = [];
        this.assets = {};

        /**
         * The offset of the sprite towards its parent. This is only set if 
         * this sprite is attached as a child sprite towards a parent.
         */
        this.offset = null;
    }

}