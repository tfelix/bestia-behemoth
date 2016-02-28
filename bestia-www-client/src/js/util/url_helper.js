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