import 'knockout';

/**
 * Offsets of multirpites in regard to the main sprite.
 * 
 * @export
 * @class Offset
 */
export default class Offset {

    constructor() {
        
        this.targetSprite = ko.observable();
        this.defaultOffset = ko.observable();
        this.offsets = ko.observableArray();

    }
    

};
