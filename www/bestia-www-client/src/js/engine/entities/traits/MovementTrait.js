import { engineContext } from '../../EngineData';
import Trait from './Trait';
import LOG from '../../../util/Log';
import WorldHelper from '../../map/WorldHelper';
import { addEntityAnimation } from './VisualTrait';

/**
 * Adds a movement structure to an entity.
 * 
 * @param {object} entity - The entity to add the movement structure to.
 * @param {array} path - An array containing points (x and y objects) to describe a movement path.
 * @param {float} walkspeed - The walkspeed of the entity.
 * @param {delta} delta - The time delay for this movement and this client. The movement 
 * has started since this time already and must be speed up to compensate for this.
 */
export function addEntityMovement(entity, path, walkspeed, delta) {

	walkspeed = walkspeed || 1;
	delta = delta || 0;

	entity.movement = {
		path: path,
		speed: walkspeed,
		delta: delta
	};
}

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

/**
 * This is a move advanced checking algorithm for the position. It will see
 * if the entity is moving towards the current point and until there is a
 * threshold it will only fasten the movement speed to reach the given point
 * or slow down the movement if the sprite has already passed it. For
 * smoother calculation we weill be in pixel space.
 */
function setPosition(sprite, x, y) {

	// Position directly if we are actually not moving.
	if (!isMoving(sprite, x, y)) {
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
}

/**
 * Returns the animation name for walking to this position, from the old
 * position.
 * 
 * @param oldPos
 * @param newPos
 */
function getWalkAnimationName(oldTile, newTile) {

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
}

/**
 * Returns the animation name for standing to this position.
 * 
 * @param oldPos
 * @param newPos
 */
function getStandAnimationName(oldTile, newTile) {
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
}

/**
 * Returns true if the entity is currently moving.
 */
function isMoving(sprite) {
	return sprite._movingTween !== null && sprite._movingTween.isRunning;
}

/**
 * Checks if there is a movement patch attached to an entity. If so this
 * trait will perform the rendering of the movement.
 */
export class MovementTrait extends Trait {

	constructor(game) {
		super();

		if (!game) {
			throw 'game can not be null.';
		}

		this._game = game;
	}

    /**
     * Checks if the given entity contains the movement trait.
     * @param {object} entity Entity object. 
     */
	hasTrait(entity) {
		return entity.hasOwnProperty('movement');
	}

	handleTrait(entity, sprite) {
		LOG.info('Moving entity: ' + entity.eid);

		this.spriteMovePath(sprite, entity);

		delete entity.movement;
	}

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
	spriteMovePath(sprite, entity) {

		let path = entity.movement.path;
		let speed = entity.movement.walkspeed || 1;

		// Push current position of the entity (start) to the path aswell.
		path.unshift(WorldHelper.getTileXY(sprite.x, sprite.y));

		sprite.tweenMove = this._game.add.tween(sprite);

		let currentPathCounter = 0;
		let currentPath = path;

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

		// After each child tween has completed check the next walking direction and
		// update the entity movement.
		sprite.tweenMove.onChildComplete.add(function () {
			currentPathCounter++;

			var isLast = currentPath.length + 1 === currentPathCounter;
			if (isLast) {
				// We keep standing still now.
			} else {
				var currentPosition = currentPath[currentPathCounter];
				var nextAnim = getWalkAnimationName(currentPosition, path[currentPathCounter + 1]);
				addEntityAnimation(entity, nextAnim);
			}

		}, this);

		// At the end of the movement fetch the correct standing animation
		// and stop the movement.
		sprite.tweenMove.onComplete.addOnce(function () {

			var size = path.length;
			var currentPos = path[size - 1];
			var lastPos = path[size - 2];
			var nextAnim = getStandAnimationName(lastPos, currentPos);
			addEntityAnimation(entity, nextAnim);

			this.position = currentPos;
		}, this);

		// Start the first animation immediately, because the usual checks
		// only start to check after the first tween has finished.
		var nextAnim = getWalkAnimationName(path[0], path[1]);
		addEntityAnimation(entity, nextAnim);
		sprite.tweenMove.start();
	}
}