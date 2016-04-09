/**
 * This class is responsible for loading the description files of entities. It
 * will determine from the context in which URL it needs to look. All loading
 * operations are backed by the DemandLoader and thus are asynchronous.
 */
Bestia.Engine.DescriptionLoader = function(loader, urlHelper) {

	this._loader = loader;
	this._url = urlHelper;

};

/**
 * Returns the description JSON file if it has already been loaded. Null
 * otherwise.
 * 
 * @param name
 */
Bestia.Engine.DescriptionLoader.prototype.getDescription = function(name) {
	if((typeof name) !== 'string') {
		name = this._getNameFromData(name) + '_desc';
	}
	
	return this._loader.get(name, 'json');
};

Bestia.Engine.DescriptionLoader.prototype.loadDescription = function(data, fnCallback) {

	var fnCallback = fnCallback || Bestia.NOOP;

	var url = this._getUrlFromData(data);
	var name = this._getNameFromData(data) + '_desc';

	this._loader.load({
		key : name,
		type : 'json',
		url : url
	}, function() {
		var descFile = this.getDescription(name);
		fnCallback(descFile);
	}.bind(this));
};

Bestia.Engine.DescriptionLoader.prototype._getUrlFromData = function(data) {

	switch (data.t) {
	case 'MOB_ANIM':
	case 'PLAYER_ANIM':
		// its an mob.
		return this._url.getMobDescUrl(data.s);
	default:
		// its an object.
		return this._url.getObjectDescUrl(data.s);
		break;
	}
};

Bestia.Engine.DescriptionLoader.prototype._getNameFromData = function(data) {
	return data.s;
};