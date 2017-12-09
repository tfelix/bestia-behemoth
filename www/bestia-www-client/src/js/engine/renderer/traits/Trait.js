
/**
 * This traits describe some data structures added to entities which are used 
 * inside the renderer to display the special entity traits.
 */
export default class Trait {

	constructor() {
		// no op
	}

    /**
     * Checks if the given entity contains the movement trait.
     * @param {object} entity Entity object. 
     */
	hasTrait(entity) {
		throw 'hasTrait must be overwritten.';
	}

    /**
     * Handle the event if a entity has a special trait attached to its datastructure.
     * @param {object} entity Entity object describing the entity.
     * @param {PhaserJS.Sprite} sprite Sprite object from PhaserJS.
     */
	handleTrait(entity, sprite) {
		throw 'handelTrait must be overwritten.';
	}

	/**
	 * Callback after all entities have been iterated. May be needed for some cleanup work.
	 */
	postEntityIteration() {
		// no op.
	}
}