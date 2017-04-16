/**
 * @typedef {Object} SubAnim
 * @property {string} sprite Name of the subsprite.
 * @property {string} animation Name of the animation to be played.
 */

/**
 * This class helps to return the name of the subsprite animations of a given
 * multi sprite data object.
 */
export default class WalkAnimationMultiController {

	/**
	 * Simple ctor.
	 * 
	 * @param {SpriteAnimationCache} spriteCache - Cache for the sprite animations.
	 */
	constructor(spriteCache) {
		if (!spriteCache) {
			throw 'SpriteCache can not be null.';
		}

		this._cache = spriteCache;
	}

	/**
	 * Gets the names of the subsprite animations for a given animation on a sprite. 
	 * @param {string} spriteName 
	 * @param {string} currentAnimation
	 * @returns {SubAnim[]} The associated animations for the sub-sprites.
	 */
	getAnimationName(spriteName, currentAnimation) {
		var desc = this._cache.getcurrentDescription(spriteName);

		if (desc.type !== 'multisprite') {
			throw 'Sprite is not of type multisprite.';
		}

		var anims = [];
		var multisprites = desc.multiSprite;

		multisprites.forEach(function (multisprite) {
			var offset = this._cache.getOffsetDescription(spriteName, multisprite);
			if (!offset.hasOwnProperty(currentAnimation)) {
				return;
			}
			anims.push({
				name: multisprite,
				animation: offset[currentAnimation].name
			});
		}, this);

		return anims;
	}
}