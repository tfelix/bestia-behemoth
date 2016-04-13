/**
 * Baseclass of the builder.
 */
Bestia.Engine.Builder = function(factory, ctx) {
	
	this._factory = factory;
	
	this._ctx = ctx;

};

Bestia.Engine.Builder.prototype.build = function(data, desc) {
	
	throw new Error("Must be overwritten by child class.");
	
};

Bestia.Engine.Builder.prototype.canBuild = function(data, desc) {
	return data.type === this.type && data.version === this.version;
};

Bestia.Engine.Builder.prototype.load = function(descFile, fnOnComplete) {
	
	var pack = descFile.assetpack;
	this._ctx.loader.loadPackData(pack, fnOnComplete);

};