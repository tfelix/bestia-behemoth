import 'knockout';

/**
 * A point representation managed via knockout.
 * 
 * @export
 * @class Point
 */
export default class Point {
    
    /**
     * Creates an instance of Point.
     * @param {number} x X coordinate of this point.
     * @param {number} y Y coordinate of this point.
     * @memberof Point
     */
    constructor(x = 0, y = 0) {

        this.x = ko.observable(x);
        this.y = ko.observable(y);

    }
};
