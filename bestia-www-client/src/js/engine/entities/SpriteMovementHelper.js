import { engineContext } from '../EngineData';
import LOG from '../../util/Log';

/**
 * Helper methods and function to perform sprite entity manipulations on phaser sprites.
 */

const FACING = Object.freeze({
    TOP: 1,
    TOP_RIGHT: 2,
    RIGHT: 3,
    BOTTOM_RIGHT: 4,
    BOTTOM: 5,
    BOTTOM_LEFT: 6,
    LEFT: 7,
    TOP_LEFT: 8
});

/**
 * Calculates the duration in ms of the total walk of the given path.
 * Depends upon the relative walkspeed of the entity.
 * 
 * @private
 * @returns Total walkspeed in ms.
 */
function getWalkDuration(length, walkspeed) {
    // Usual walkspeed is 1.4 tiles / s -> 0,74 s/tile.
    return Math.round((1 / 1.4) * length / walkspeed * 1000);
}

var SpriteMovementHelper = {

    /**
     * Moves the entity along a certain path. The path is an array with {x: INT,
     * y: INT} components. The path must not contain the current position of the
     * entity.
     * 
     * @param {Array[]} -
     *            Array containing point objects {x: Number, y: Number} objects.
     * @param {float}
     *            speed - The movement speed.
     */
    moveTo: function (sprite, path, speed = 1.0) {

        // Push current position of the entity (start) to the path aswell.
        path.unshift(this.getPosition());

        sprite.tweenMove = engineContext.game.add.tween(sprite);

        this._currentPathCounter = 0;
        this._currentPath = path;

        // Calculate coordinate arrays from path.
        path.forEach(function (ele, i) {
            // This is our current position. No need to move TO this position.
            if (i === 0) {
                return;
            }

            var cords = WorldHelper.getSpritePxXY(ele.x, ele.y);

            // We go single tile steps.
            var duration = getWalkDuration(1, speed);
            var lastTile = path[i - 1];

            // Check if we go diagonal to adjust speed.
            var distance = WorldHelper.getDistance(lastTile, ele);
            if (distance > 1.01) {
                // diagonal move. Multi with sqrt(2).
                duration *= 1.414;
            }

            // Start the animation.
            sprite.tweenMove.to({
                x: cords.x,
                y: cords.y
            }, duration, Phaser.Easing.Linear.None, false);
        }, this);

        // After each child tween has completed check if 
        sprite.tweenMove.onChildComplete.add(function () {
            this._currentPathCounter++;

            var currentPosition = this._currentPath[this._currentPathCounter];
            var isLast = this._currentPath.length === (this._currentPathCounter - 1);


            var nextAnim = this._getWalkAnimationName(pos, path[this._currentPathCounter + 1]);

            this.playAnimation(nextAnim, isLast);

        }, this);

        // At the end of the movement fetch the correct standing animation
        // and stop the movement.
        sprite.tweenMove.onComplete.addOnce(function () {

            var size = path.length;
            var currentPos = path[size - 1];
            var lastPos = path[size - 2];
            var nextAnim = this._getStandAnimationName(lastPos, currentPos);

            this.playAnimation(nextAnim);

            this.position = currentPos;

        }, this);

        // Start the first animation immediately, because the usual checks
        // only start to check after the first tween has finished.
        var animName = this._getWalkAnimationName(path[0], path[1]);
        this.playAnimation(animName);
        sprite.tweenMove.start();
    },

    /**
     * This is a move advanced checking algorithm for the position. It will see
     * if the entity is moving towards the current point and until there is a
     * threshold it will only fasten the movement speed to reach the given point
     * or slow down the movement if the sprite has already passed it. For
     * smoother calculation we weill be in pixel space.
     */
    setPosition: function (sprite, x, y) {

        // Position directly if we are not moving.
        if(!this.isMoving(sprite, x, y)) {
            sprite.x = x;
            sprite.y = y;
            return;
        }

        let posPx = WorldHelper.getPxXY(x, y);
        let curPosPx = this.getPositionPx();

        // We need to check if we are moving away or towards our given position.
        let curPathPos = this._currentPath[this._currentPathCounter];
        var curPathPosPx = WorldHelper.getPxXY(curPathPos.x, curPathPos.y);

        let d = WorldHelper.getDistance(curPosPx, curPathPosPx);

        if (isNaN(d)) {
            // Some error occured while calculating the distance.
            LOG.warn('Error while calculating the distance.');
            return;
        }

        // TODO Do some lerping here.


    },

    /**
     * Returns the animation name for walking to this position, from the old
     * position.
     * 
     * @param oldPos
     * @param newPos
     */
    getWalkAnimationName: function (oldTile, newTile) {

        var x = newTile.x - oldTile.x;
        var y = newTile.y - oldTile.y;

        if (x > 1) {
            x = 1;
        }
        if (y > 1) {
            y = 1;
        }

        var animName = '';

        if (x === 0 && y === -1) {
            return 'walk_up';
        } else if (x === 1 && y === -1) {
            animName = 'walk_up_right';
        } else if (x === 1 && y === 0) {
            animName = 'walk_right';
        } else if (x === 1 && y === 1) {
            animName = 'walk_down_right';
        } else if (x === 0 && y === 1) {
            animName = 'walk_down';
        } else if (x === -1 && y === 1) {
            animName = 'walk_down_left';
        } else if (x === -1 && y === 0) {
            animName = 'walk_left';
        } else {
            animName = 'walk_up_left';
        }

        return animName;
    },

    /**
	 * Returns the animation name for standing to this position.
	 * 
	 * @param oldPos
	 * @param newPos
	 */
    getStandAnimationName: function (oldTile, newTile) {
        var x = newTile.x - oldTile.x;
        var y = newTile.y - oldTile.y;

        if (x > 1) {
            x = 1;
        }
        if (y > 1) {
            y = 1;
        }

        if (x === 0 && y === -1) {
            return 'stand_up';
        } else if (x === 1 && y === -1) {
            return 'stand_up_right';
        } else if (x === 1 && y === 0) {
            return 'stand_right';
        } else if (x === 1 && y === 1) {
            return 'stand_down_right';
        } else if (x === 0 && y === 1) {
            return 'stand_down';
        } else if (x === -1 && y === 1) {
            return 'stand_down_left';
        } else if (x === -1 && y === 0) {
            return 'stand_left';
        } else {
            return 'stand_up_left';
        }
    },

    /**
     * Plays a specific animation. If it is a walk animation then by the name of
     * the animation the method takes core of flipping the sprite for mirrored
     * animations. It also handles stopping running animations and changing
     * between them.
     * 
     * @public
     * @param {String} name - Name of the animation to play.
     */
    playAnimation: function (sprite, entityData, name) {

        LOG.debug('Playing animation: ' + name);

        // If the animation is already playing just leave it.
        if (name === sprite.animations.name) {
            return;
        }

        // We need to mirror the sprite for right sprites.
        if (name.indexOf('right') !== -1) {
            sprite.scale.x = -1 * this._data.scale;
            name = name.replace('right', 'left');
        } else {
            sprite.scale.x = this._data.scale;
        }

        // Check if the sprite 'knows' this animation. If not we have several
        // fallback strategys to test before we fail.
        if (!this._hasAnimationName(name)) {
            name = this._getAnimationFallback(name);

            if (name === null) {
                LOG.warn('Could not found alternate animation solution for: ' + name);
                return;
            }
        }

        sprite.play(name);
    },

    /**
     * Tests if the entity sprite supports a certain animation name. It gets a
     * bit complicated since some animations are implemented purely in software
     * (right walking a mirror of left walking).
     * 
     * @param name
     */
    _hasAnimationName(name) {
        if (name.indexOf('right') !== -1) {
            name = name.replace('right', 'left');
        }

        return this._availableAnimationNames.indexOf(name) !== -1;
    },

	/**
	 * Tries to find a alternate animation. If no supported animation could be
	 * found, we will return null.
	 * 
	 * @private
	 * @return {String} - Newly found animation to display, or NULL if no
	 *         suitable animation could been found.
	 */
    _getAnimationFallback: function (name) {
        if (name === 'stand_down_left' || name === 'stand_left') {
            // Try to replace it with stand down.
            if (this._availableAnimationNames.indexOf('stand_down') === -1) {
                return null;
            } else {
                return 'stand_down';
            }
        }

        if (name == 'stand_left_up') {
            // Try to replace it with stand down.
            if (this._availableAnimationNames.indexOf('stand_up') === -1) {
                return null;
            } else {
                return 'stand_up';
            }
        }

        if (name == 'stand_right') {
            // Try to replace it with stand down.
            if (this._availableAnimationNames.indexOf('stand_up') === -1) {
                return null;
            } else {
                return 'stand_up';
            }
        }

        return null;
    },

    /**
     * Returns true if the entity is currently moving.
     */
    isMoving: function (sprite) {
        return sprite._movingTween !== null && sprite._movingTween.isRunning;
    }
}


export { SpriteMovementHelper as default };