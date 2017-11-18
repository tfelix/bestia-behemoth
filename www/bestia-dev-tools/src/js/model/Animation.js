import 'knockout';

/**
 * Holds information about a bestia animation for a sprite.
 * 
 * @export
 * @class Animation
 */
export default class Animation {
    
    constructor() {

        this.name = ko.observable();
        this.from = ko.observable();
        this.to = ko.observable();
        this.fps = ko.observable();

    }
};
