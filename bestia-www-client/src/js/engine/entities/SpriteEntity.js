import Entity from './Entity.js';
import Signal from './../../io/Signal';
import WorldHelper from '../map/WorldHelper.js';
import LOG from '../../util/Log';

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

export default class SpriteEntity extends Entity {
	
	constructor(ctx, id, x, y, desc) {
		super(ctx, id);
		
		x = x || 0;
		y = y || 0;
		
		if(desc === undefined) {
			throw 'Description can not be undefined.';
		}

		this._data = desc;

		/**
		 * Holds a list with names of supported animations. This is a cache and
		 * is generated from the JSON sprite description.
		 * 
		 * @private
		 * @property {Array}
		 */
		this._availableAnimationNames = [];
		
		this._currentPathCounter = 0;
		this._currentPath = null;
		this._tween = null;
		
		// Set the sprite now.
		this.setSprite(desc.name);

		this.setPosition(x, y);
	}

	getRootVisual() {
		return this._sprite;
	}

	/**
	 * Sets the sprite of this entity for the given sprite name.
	 */
	setSprite(spriteName) {

		// Generate the animation names.
		this._availableAnimationNames = this._data.animations.map(function(val) {
			return val.name;
		});

		this._sprite = this._game.add.sprite(0, 0, spriteName);

		this._setupSprite(this._sprite, this._data);
	}
	
	/**
	 * Some code to find a standing animation.
	 */
	__standAnimation() {
		// Find all animations in which it stands.
		var standAnimations = this._data.animations.filter(function(anim) {
			return anim.name.indexOf('stand') !== -1;
		});

		// Pick a custom one.
		var i = Math.floor(Math.random() * standAnimations.length);
		this.playAnimation(standAnimations[i].name);
	}

	/**
	 * Helper function to setup a sprite with all the information contained
	 * inside a description object.
	 * 
	 * @param sprite
	 * @param descObj
	 */
	_setupSprite(sprite, descObj) {

		// Setup the normal data.
		sprite.anchor = descObj.anchor || {
			x : 0.5,
			y : 0.5
		};
		sprite.scale.setTo(descObj.scale || 1);
		// Sprite is invisible at first.
		sprite.alpha = 0;

		var anims = descObj.animations || [];

		// Register all the animations of the sprite.
		anims.forEach(function(anim) {
			var frames = Phaser.Animation.generateFrameNames(anim.name + '/', anim.from, anim.to, '.png', 3);
			sprite.animations.add(anim.name, frames, anim.fps, true, false);
		}, this);
		
		this._setupCallbacks(sprite);
	}

	setTexture(name) {
		this._sprite.loadTexture(name, 0);
	}

	/**
	 * Calculates the duration in ms of the total walk of the given path.
	 * Depends upon the relative walkspeed of the entity.
	 * 
	 * @private
	 * @method Bestia.Engine.Entity#_getWalkDuration
	 * @returns Total walkspeed in ms.
	 */
	_getWalkDuration(length, walkspeed) {
		// Usual walkspeed is 1.4 tiles / s -> 0,74 s/tile.
		return Math.round((1 / 1.4) * length / walkspeed * 1000);
	}

	show() {
		this._sprite.alpha = 1;
	}

	appear() {
		this._sprite.alpha = 1;
	}

	/**
	 * Sprite position is updated with the data from the server.
	 */
	update(msg) {
		var x = msg.x - this.position.x;
		var y = msg.y - this.position.y;
		var distance = Math.sqrt(x * x + y * y);

		// Directly set distance if too far away.
		if (distance > 1.5) {
			this.setPosition(msg.x, msg.y);
			return;
		}

		this.moveTo([ {
			x : msg.x,
			y : msg.y
		} ]);
	}

	/**
	 * Stops rendering of this entity and removes it from the scene.
	 */
	remove() {

		if(this._tween !== null) {
			this._tween.stop();
		}
		
		this._sprite.destroy();

	}

	/**
	 * Plays a specific animation. If it is a walk animation then by the name of
	 * the animation the method takes core of flipping the sprite for mirrored
	 * animations. It also handles stopping running animations and changing
	 * between them.
	 * 
	 * @public
	 * @method Bestia.Engine.Entity#playAnim
	 * @param {String}
	 *            name - Name of the animation to play.
	 */
	playAnimation(name) {

		// If the animation is the same. Just let it run.
		if (name === this._sprite.animations.name) {
			return;
		}

		// We need to mirror the sprite for right sprites.
		if (name.indexOf('right') !== -1) {
			this._sprite.scale.x = -1 * this._data.scale;
			name = name.replace('right', 'left');
		} else {
			this._sprite.scale.x = this._data.scale;
		}

		// Check if the sprite 'knows' this animation. If not we have several
		// fallback strategys to test before we fail.
		if (!this._hasAnimationName(name)) {
			name = this._getAnimationFallback(name);

			if (name === null) {
				console.warn('Could not found alternate animation solution for: ' + name);
				return;
			}
		}

		this._sprite.play(name);
	}

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
	}

	/**
	 * Tries to find a alternate animation. If no supported animation could be
	 * found, we will return null.
	 * 
	 * @private
	 * @return {String} - Newly found animation to display, or NULL if no
	 *         suitable animation could been found.
	 */
	_getAnimationFallback(name) {
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
	}

	/**
	 * Stops a current movement.
	 * 
	 * @public
	 * @method Bestia.Engine.Entity#stopMove
	 */
	stopMove() {

		if (this._tween) {
			this._tween.stop();
		}

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
	moveTo(path, speed = 1.0) {

		// Push current position of the entity (start) to the path aswell.
		path.unshift(this.getPosition());

		// Stop movement is there is any currently.
		this.stopMove();

		this._tween = this._game.add.tween(this._sprite);

		this._currentPathCounter = 0;
		this._currentPath = path;

		// Calculate coordinate arrays from path.
		path.forEach(function(ele, i) {
			// This is our current position. No need to move TO this position.
			if (i === 0) {
				return;
			}

			var cords = WorldHelper.getSpritePxXY(ele.x, ele.y);

			// We go single tile steps.
			var duration = this._getWalkDuration(1, speed);
			var lastTile = path[i - 1];

			// Check if we go diagonal to adjust speed.
			var distance = WorldHelper.getDistance(lastTile, ele);
			if (distance > 1.01) {
				// diagonal move. Multi with sqrt(2).
				duration *= 1.414;
			}

			// Start the animation.
			this._tween.to({
				x : cords.x,
				y : cords.y
			}, duration, Phaser.Easing.Linear.None, false);
		}, this);

		this._tween.onChildComplete.add(function() {
			this._currentPathCounter++;

			var pos = this._currentPath[this._currentPathCounter];
			var isLast = this._currentPath.length === (this._currentPathCounter - 1);
			// We dont need no checks since WE know where we are (or at least we
			// think so).
			this._uncheckedSetPosition(pos.x, pos.y);

			var nextAnim = this._getWalkAnimationName(pos, path[this._currentPathCounter + 1]);

			this.playAnimation(nextAnim, isLast);

		}, this);

		this._tween.onComplete.addOnce(function() {

			var size = path.length;
			var currentPos = path[size - 1];
			var lastPos = path[size - 2];
			var nextAnim = this._getStandAnimationName(lastPos, currentPos);

			this.playAnimation(nextAnim);

			this.position = currentPos;

		}, this);

		// Start first animation immediately.
		var animName = this._getWalkAnimationName(path[0], path[1]);
		this.playAnimation(animName);
		this._tween.start();
	}
	
	/**
	 * Helper function wo we can call super method from callback.
	 */
	_uncheckedSetPosition(x ,y) {
		super.setPosition(x, y);
	}

	/**
	 * This is a move advanced checking algorithm for the position. It will see
	 * if the entity is moving towards the current point and until there is a
	 * threshold it will only fasten the movement speed to reach the given point
	 * or slow down the movement if the sprite has already passed it. For
	 * smoother calculation we weill be in pixel space.
	 */
	setPosition(x, y) {
		
		// Position directly if we are not moving.
		if(!this.isMoving) {
			super.setPosition(x, y);
			return;
		}
		
		let posPx = WorldHelper.getPxXY(x, y);
		let curPosPx = WorldHelper.getPxXY(this.getPosition().x, this.getPosition().y);

		// We need to check if we are moving away or towards our given position.
		let curPathPos = this._currentPath[this._currentPathCounter];
		var curPathPosPx = WorldHelper.getPxXY(curPathPos.x, curPathPos.y);
		
		let d = WorldHelper.getDistance(curPosPx, curPathPosPx);
		
		if(isNaN(d)) {
			// Some error occured while calculating the distance.
			LOG.warn('Error while calculating the distance.');
			return;
		}
		
		/*
		 * if(d > WorldHelper.TILE_SIZE / 2) { // we are further away then close
		 * to the target. Need to know if we // move away or closer. let
		 * nextPathPos = this._currentPath[this._currentPathCounter + 1]; let
		 * nextPathPosPx = WorldHelper.getPxXY(path.x, path.y); let nextD =
		 * WorldHelper.getDistance(curPosPx, nextPathPosPx);
		 * 
		 * if(nextD < d) { // We are moving towards the next tile and are too
		 * fast. Need to slow down. } else { // We are still moving to the tile
		 * before and are too slow. Need to fasten. } } else { }
		 */
		
	}

	/**
	 * Returns the animation name for walking to this position, from the old
	 * position.
	 * 
	 * @param oldPos
	 * @param newPos
	 */
	_getWalkAnimationName(oldTile, newTile) {

		var x = newTile.x - oldTile.x;
		var y = newTile.y - oldTile.y;
		
		if(x > 1) {
			x = 1;
		}
		if(y > 1) {
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
	_getStandAnimationName(oldTile, newTile) {
		var x = newTile.x - oldTile.x;
		var y = newTile.y - oldTile.y;
		
		if(x > 1) {
			x = 1;
		}
		if(y > 1) {
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
	get isMoving() {
		return this._tween !== null && this._tween.isRunning;
	}
}