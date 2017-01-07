/**
 * Baseclass of the builder. It is used automatically to create map entities
 * (bestias, items etc.)
 */
export default class Builder {
	
	constructor(factory, ctx) {
		
		this._factory = factory;
		
		this._ctx = ctx;
	}

	/**
	 * Does the creation job of an delivered data message object.
	 */
	build() {
		
		throw new Error("Must be overwritten by child class.");
		
	}

	/**
	 * Checks whenever a concrete builder can generate/build a given dataset.
	 */
	canBuild(data) {
		return data.type === this.type && data.version === this.version;
	}

	/**
	 * Loads all needed assets in order to perform a visual build of this
	 * entity. The fnOnComplete is called when all data has been loaded.
	 */
	load(descFile, fnOnComplete) {	
		var pack = descFile.assetpack;
		this._ctx.loader.loadPackData(pack, fnOnComplete);
	}
}