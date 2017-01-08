import Entity from './Entity.js';
import WorldHelper from '../map/WorldHelper.js';


export default class SpriteEntity extends Entity {
	
	constructor(ctx, id, x, y, desc) {
		super(ctx, id);

		this._data = desc;

		/**
		 * Holds a list with names of supported animations. This is a cache and
		 * is generated from the JSON sprite description.
		 * 
		 * @private
		 * @property {Array}
		 */
		this._availableAnimationNames = [];

		this.setPosition(x, y);

		this._currentPathCounter = 0;
		this._currentPath = null;

		this._tween = null;
	}

	setSprite(spriteName) {

		// Generate the animation names.
		this._availableAnimationNames = this._data.animations.map(function(val) {
			return val.name;
		});

		this._sprite = this._game.add.sprite(0, 0, spriteName);

		this._setupSprite(this._sprite, this._data);

		// Find all animations which stands.
		var standAnimations = this._data.animations.filter(function(anim) {
			return anim.name.indexOf('stand') !== -1;
		});

		var i = Math.floor(Math.random() * standAnimations.length);
		// var i = 0;
		this.playAnimation(standAnimations[i].name);

		// Re-set position so the sprite gets now postioned.
		this.setPosition(this.position.x, this.position.y);
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
		// Usual walkspeed is 3 tiles / s -> 1/3 s/tile.
		return Math.round((1 / 3) * length / walkspeed * 1000);
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

		this._tween.stop();
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
		path.unshift(this.position);

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
			this.setPosition(pos.x, pos.y, true);

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
	 * This will check the current position with the given position. If the
	 * bestia is currently moving this will blend the movement towards the given
	 * position by a certain algorithm. If the distance is too big it will hard
	 * set the position.
	 */
	checkPosition(x, y) {

		var newPos = {
			x : x,
			y : y
		};

		// Compare the current position with the NOW position.
		var d = this._getDistance(this.position, newPos);

		// Now we decide, are we moving?
		if (this.isMoving) {
			// Check the distance.
			// if (d < 2) {
				// Are we approaching the target?
				/*
				 * if (true) { // if so speed up the movement. } else if (false) { //
				 * if we are heading away from the target but we once passed it, //
				 * slow down. } else { // If the point was not even found in
				 * movement list we cancel // movement and calc path towards
				 * goal. }
				 */
			// } else {
				// Set to the target.
				this.stopMove();
				this.position = newPos;
			// }
		} else {
			// We stand. So we MUST move to the target position.
			if (d < 1.5) {
				// Move to target.
				this.moveTo([ newPos ], 1.5);
			} else {
				// Set target.
				this.position = newPos;
			}
		}

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