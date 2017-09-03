
/**
 * This class holds globally all sprite animation data of instanced sprites. It should be asked
 * if a sprite wants to perform an animation this cache is asked if the animation does exist.
 * If the sprite description is loaded the first time the description object is feed into the 
 * SpriteAnimationCache and the data is parsed into a usable format.
 * If there is an animation requested which does not exist it will return an alternative usable
 * animation name.
 */
export default class SpriteDescriptionCache {

	constructor() {

		this._data = {};
	}

	/**
	 * Adds the given sprite description to the manager and parses its data to a better usable
	 * format for the manager.
	 * 
	 * @public
	 * @param {object} desc - Description for the sprite object.
	 */
	addSpriteDescription(desc) {

		this._data[desc.name] = {
			data: desc,
			availableAnimationNames: this._getAnimationNames(desc)
		};

	}

	/**
	 * Extracts the available animation names from a description data file.
	 */
	_getAnimationNames(data) {
		return data.animations.map(function (val) {
			return val.name;
		});
	}

	getRandomStandAnimation(spriteName) {
		// Find all animations in which it stands.
		var standAnimations = this._data[spriteName].animations.filter(function(anim) {
			return anim.name.indexOf('stand') !== -1;
		});

		// Pick a custom one.
		var i = Math.floor(Math.random() * standAnimations.length);
		return standAnimations[i];
	}

	getOffsetDescription(spriteName, multisprite) {
		throw 'implement';
	}

	/**
	 * Clears and resets the cache in its original state thus deleting all the cached data.
	 * 
	 * @public
	 */
	clear() {
		this._data = {};
	}
}