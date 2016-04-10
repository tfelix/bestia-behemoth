
/**
 * Helper utility to resolve URLs for the bestia asset system.
 */
export default class UrlHelper {
	/**
	 * @param {string}
	 *            assetUrl - Base URL for the assets.
	 */
	constructor(assetUrl) {

		/**
		 * @property {string}
		 */
		this._assetRoot = assetUrl;

	}
	
	/**
	 * Returns the url to the attack icon.
	 * 
	 * @param {string}
	 *            atkName - Name of the attack icon (attack db name).
	 * @returns {string} URL of the attack icon.
	 */
	getAttackIconUrl(atkName) {
		return this._assetRoot + 'img/icons/attack/' + atkName + '.png';
	}
	
	/**
	 * Returns the URL inside the asset folder for the mob icon.
	 * 
	 * @param mobName
	 *            Database name of the mob.
	 * @returns {String} URL of the mob icon.
	 */
	getMobIconUrl(mobName) {
		return this._assetRoot + 'img/icons/mob/' + mobName + '.png';
	}

	getMapPackUrl(mapName) {
		return this._assetRoot + 'map/' + mapName + '/assetpack.json';
	}

	getMobDescUrl(mobName) {
		return this._assetRoot + 'sprite/mob/' + mobName + '/' + mobName + '_desc.json';
	}

	getObjectDescUrl(objectName) {
		return this._assetRoot + 'sprite/object/' + objectName + '/' + objectName + '_desc.json';
	}

	getMobPackUrl(mobName) {
		return this._assetRoot + 'mob/' + mobName + '/' + mobName + '_pack.json';
	}

	getIndicatorUrl(indicator) {
		if(indicator === 'cursor') {
			// Special treatmen for default cursor.
			return this._assetRoot + 'cast_indicators/' + indicator + '.png';
		} 
		return this._assetRoot + 'cast_indicators/' + indicator + '/' + indicator + '.png';
	}

	getItemIconUrl(itemName) {
		if(!itemName.endsWith('.png')) {
			itemName += '.png';
		}
		return this._assetRoot + 'img/items/' + itemName;
	}

	getImageUrl(imgName) {
		return this._assetRoot + 'img/' + imgName + '.png';
	}

	getFilterUrl(filter) {
		return this._assetRoot + 'filter/' + filter + '.js';
	}

	getSpriteUrl(sprite) {
		return this._assetRoot + 'img/sprite/' + sprite + '.png';
	}
}


