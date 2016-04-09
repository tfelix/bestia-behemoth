/**
 * Baseclass of the builder.
 */
Bestia.Engine.Builder = function() {
	

};

Bestia.Engine.Builder.prototype.build = function(data, desc) {
	
	if (!this._isSane(desc)) {
		// Can not build.
		return;
	}
	
	return this._build(data, desc);
	
};

Bestia.Engine.Builder.prototype._build = function() {
	throw new Error("Must be overwritten by child class.");
};

Bestia.Engine.Builder.prototype._isSane = function(data) {
	return data.type === this.type && data.version === this.version;
};