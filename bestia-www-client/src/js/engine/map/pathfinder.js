import EasyStar from 'easystarjs';

const MAX_ITER_PER_FRAME = 1000;

/**
 * Helper functions to calculate a walkable path client side.
 */
 class Pathfinder {

    constructor() {
        
        this._easystar = new EasyStar.js();
        this._easystar.enableDiagonals();
        this._easystar.setIterationsPerCalculation(MAX_ITER_PER_FRAME);
        this._offset = {x: 0, y: 0};

        this._calculationInstanceId = 0;
    }
    
    /**
     * 
     * @param {Point} offset The current offset of the given map grid array.
     * @param {array} gridArray The array with tile ids.
     */
    setGrid(offset, gridArray) {

        this._offset = offset;
        this._easystar.setGrid(gridArray);
    }

    setAcceptableTiles(acceptedTiles) {

        this._easystar.setAcceptableTiles(acceptedTiles);
    }

    findPath(start, end, fn) {

        // Wrap the call function with our own to reset path
        // calculation flag.
        var myFn = function(path) {
            this._calculationInstanceId = 0;
            
            if(path == null) {
                fn(null);
            }

            // Recalculate the offset parameters.
            for(var i = 0; i < path.length; i++) {
                path[i].x = path[i].x + this._offset.x;
                path[i].y = path[i].y + this._offset.y;
            }

            // finally call the callback.
            fn(path);
           
        }.bind(this);

        var x1 = start.x - this._offset.x;
        var y1 = start.y - this._offset.y;
        var x2 = end.x - this._offset.x;
        var y2 = end.y - this._offset.y;

        this._calculationInstanceId = this._easystar.findPath(x1, y1, x2, y2, myFn);
    }

    /**
     * Should be called in the update loop to perform inbetween frame 
     * calculation and dont produce frame lag because we block for too 
     * long.
     */
    update() {

        if(this._calculationInstanceId === 0) {
            return;
        }
        
        this._easystar.calculate();
    }
 }

 var pathfinder = new Pathfinder();

 export { pathfinder as default };