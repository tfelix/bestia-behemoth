/**
 * Helper utility to resolve URLs for the bestia asset system.
 */
Bestia.UrlHelper = function(assetUrl) {

	this._assetRoot = assetUrl;

};

/**
 * Returns the URL inside the asset folder for the mob icon.
 * 
 * @param mobName
 *            Database name of the mob.
 * @returns {String} URL of the mob icon.
 */
Bestia.UrlHelper.prototype.getMobIconUrl = function(mobName) {
	return this._assetRoot + 'img/icons/mob/' + mobName + '.png';
};

Bestia.UrlHelper.prototype.getMapPackUrl = function(mapName) {
	return this._assetRoot + 'map/' + mapName + '/assetpack.json';
};

Bestia.UrlHelper.prototype.getMobDescUrl = function(mobName) {
	return this._assetRoot + 'sprite/mob/' + mobName + '/' + mobName + '_desc.json';
};

Bestia.UrlHelper.prototype.getObjectDescUrl = function(objectName) {
	return this._assetRoot + 'sprite/object/' + objectName + '/' + objectName + '_desc.json';
};

Bestia.UrlHelper.prototype.getMobPackUrl = function(mobName) {
	return this._assetRoot + 'mob/' + mobName + '/' + mobName + '_pack.json';
};

Bestia.UrlHelper.prototype.getIndicatorUrl = function(indicator) {
	return this._assetRoot + 'cast_indicators/' + indicator + '/' + indicator + '.png';
};

Bestia.UrlHelper.prototype.getItemIconUrl = function(itemName) {
	if(!itemName.endsWith('.png')) {
		itemName += '.png';
	}
	return this._assetRoot + 'img/items/' + itemName;
};

Bestia.UrlHelper.prototype.getImageUrl = function(imgName) {
	return this._assetRoot + 'img/' + imgName + '.png';
};

Bestia.UrlHelper.prototype.getFilterUrl = function(filter) {
	return this._assetRoot + 'filter/' + filter + '.js';
};

Bestia.UrlHelper.prototype.getSpriteUrl = function(sprite) {
	return this._assetRoot + 'img/sprite/' + sprite + '.png';
};