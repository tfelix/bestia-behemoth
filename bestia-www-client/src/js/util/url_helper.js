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

Bestia.UrlHelper.prototype.getMobPackUrl = function(mobName) {
	return this._assetRoot + 'mob/' + mobName + '/' + mobName + '_pack.json';
};

Bestia.UrlHelper.prototype.getItemIconUrl = function(itemName) {
	return this._assetRoot + 'img/items/' + itemName;
};

Bestia.UrlHelper.prototype.getImageUrl = function(imgName) {
	return this._assetRoot + 'img/' + imgName + '.png';
};