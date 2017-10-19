/**
 * This class performs a look up of the correct walk animation name depending on
 * the currentl occupied tile and the tile the sprite wants to walk to. The name is returned.
 */
export default class WalkAnimationController {

	constructor() {

		this._x = 0;
		this._y = 0;
	}

	/**
	 * Calculates the new state of the internal position calculation.
	 * 
	 * @param {object} oldPos - Old position. Object with x and y coordinates. 
	 * @param {object} newPos - New position. Object with x and y coordinates.
	 */
	_normTiles(oldPos, newPos) {
		this._x = newPos.x - oldPos.x;
		this._y = newPos.y - oldPos.y;

		if (this._x > 1) {
			this._x = 1;
		}
		if (this._y > 1) {
			this._y = 1;
		}
	}

	/**
	 * Returns the animation name for walking to this position, from the old
	 * position.
	 * 
	 * @param {object} oldPos
	 * @param {object} newPos
	 * @return {string} The name of the walk animation to be played.
	 */
	getWalkAnimationName(oldTile, newTile) {

		this._normTiles(oldTile, newTile);

		var animName = '';

		if (this._x === 0 && this._y === -1) {
			return 'walk_up';
		} else if (this._x === 1 && this._y === -1) {
			animName = 'walk_up_right';
		} else if (this._x === 1 && this._y === 0) {
			animName = 'walk_right';
		} else if (this._x === 1 && this._y === 1) {
			animName = 'walk_down_right';
		} else if (this._x === 0 && this._y === 1) {
			animName = 'walk_down';
		} else if (this._x === -1 && this._y === 1) {
			animName = 'walk_down_left';
		} else if (this._x === -1 && this._y === 0) {
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
	getStandAnimationName(oldTile, newTile) {

		this._normTiles(oldTile, newTile);

		if (this._x === 0 && this._y === -1) {
			return 'stand_up';
		} else if (this._x === 1 && this._y === -1) {
			return 'stand_up_right';
		} else if (this._x === 1 && this._y === 0) {
			return 'stand_right';
		} else if (this._x === 1 && this._y === 1) {
			return 'stand_down_right';
		} else if (this._x === 0 && this._y === 1) {
			return 'stand_down';
		} else if (this._x === -1 && this._y === 1) {
			return 'stand_down_left';
		} else if (this._x === -1 && this._y === 0) {
			return 'stand_left';
		} else {
			return 'stand_up_left';
		}
	}
}