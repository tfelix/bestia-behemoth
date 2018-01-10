/**
 * Baseclass of the builder. It is used automatically to create map entities
 * (bestias, items etc.)
 */
export default class Builder {
	
	constructor() {
		// no op.
	}

	/**
	 * Does the creation job of an delivered data message object.
	 */
	build() {
		
		throw 'Must be overwritten by child class.';
		
	}

	/**
	 * Checks whenever a concrete builder can generate/build a given dataset.
	 */
	canBuild(data) {

		if(!this.type || !this.version) {
			throw 'The fields type and/or version are not implemented by this class.';
		}

		return data.type === this.type && data.version === this.version;
	}

	/**
	 * Loads all needed assets in order to perform a visual build of this
	 * entity. The fnOnComplete is called when all data has been loaded.
	 */
	load(descFile, fnOnComplete) {	
		throw 'Childs must overwrite the load methood';
	}
}