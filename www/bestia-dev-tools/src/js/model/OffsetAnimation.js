import 'knockout';

/**
 * Details about the offsets for certain animation frames and names.
 * 
 * @export
 * @class OffsetAnimation
 */
export default class OffsetAnimation {
    
    constructor() {
        
        this.name = ko.observable();
        this.triggered = ko.observable();

        /**
         * @param {Point[]}
         */
        this.offsets = ko.observableArray();

    }

};
