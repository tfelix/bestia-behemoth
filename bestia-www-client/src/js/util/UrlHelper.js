
/**
 * Helper utility to resolve URLs for the bestia asset system.
 */
export default class UrlHelper {
	/**
	 * @param {string}
	 *            assetUrl - Base URL for the assets.
	 */
	constructor(assetUrl) {
		if (!assetUrl) {
			throw 'assetUrl can not be empty.';
		}

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
		return this._assetRoot + 'icons/attack/' + atkName + '.png';
	}

	/**
	 * Returns the URL inside the asset folder for the mob icon.
	 * 
	 * @param mobName
	 *            Database name of the mob.
	 * @returns {String} URL of the mob icon.
	 */
	getMobIconUrl(mobName) {
		return this._assetRoot + 'icons/mob/' + mobName + '.png';
	}

	/**
	 * Returns the url of an tilemap image.
	 * 
	 * @param {string}
	 *            name - Name of the tilemap.
	 * @returns {string} URL of the tilemap.
	 */
	getTilemapUrl(name) {
		return this._assetRoot + 'tileset/' + name + '.png';
	}

	/**
	 * Gets the URL for the nob description json file.
	 */
	getMobDescUrl(mobName) {
		return this._assetRoot + 'sprite/mob/' + mobName + '/' + mobName + '_desc.json';
	}

	/**
	 * Returns the url for the mob sprite sheet.
	 */
	getMobSheetUrl(mobName) {
		return this._assetRoot + 'sprite/mob/' + mobName + '/' + mobName + '.png';
	}

	/**
	 * Returns the url for the mob sprite sheet atlas file.
	 */
	getMobAtlasUrl(mobName) {
		return this._assetRoot + 'sprite/mob/' + mobName + '/' + mobName + '.json';
	}

	/**
	 * Returns the url for the mob sprite sheet.
	 */
	getMultiSheetUrl(mobName) {
		return this._assetRoot + 'sprite/multi/' + mobName + '/' + mobName + '.png';
	}

	/**
	 * Returns the url for the mob sprite sheet.
	 */
	getMultiDescUrl(sheetName) {
		return this._assetRoot + 'sprite/multi/' + sheetName + '/' + sheetName + '_desc.json';
	}

	/**
	 * Returns the url for the mob sprite sheet atlas file.
	 */
	getMultiAtlasUrl(mobName) {
		return this._assetRoot + 'sprite/multi/' + mobName + '/' + mobName + '.json';
	}

	/**
	 * Multi sprites have an offset file which defines the relative positions of
	 * all sprites. This method returns the url to it.
	 */
	getMultiOffsetUrl(multisprite, offsetFile) {
		return this._assetRoot + 'sprite/multi/' + multisprite + '/' + offsetFile + '.json';
	}

	getObjectDescUrl(objectName) {
		return this._assetRoot + 'sprite/object/' + objectName + '/' + objectName + '_desc.json';
	}

	getMobPackUrl(mobName) {
		return this._assetRoot + 'mob/' + mobName + '/' + mobName + '_pack.json';
	}

	getIndicatorUrl(indicator) {
		return this._assetRoot + 'sprite/indicators/' + indicator + '.png';
	}

	getItemIconUrl(itemName) {
		if (!itemName.endsWith('.png')) {
			itemName += '.png';
		}
		return this._assetRoot + 'sprite/items/' + itemName;
	}
/*
	getImageUrl(imgName) {
		return this._assetRoot + 'img/' + imgName + '.png';
	}
*/
	getFilterUrl(filter) {
		return this._assetRoot + 'filter/' + filter + '.js';
	}

	getSpriteUrl(sprite) {
		if (!sprite.endsWith('.png')) {
			sprite += '.png';
		}
		return this._assetRoot + 'sprite/single/' + sprite;
	}

	/**
	 * Returns the URL pointing to a image of a given category.
	 * 
	 * @param {string} category The image category (indicators, items, mob, multi, object, single).
	 * @param {string} name The name of the image file.
	 * @return {string} The url to the image file.
	 * 
	 */
	getSpriteUrl2() {
		return this._assetRoot + 'sprite/' + category + '/' + name;
	}

	/**
	 * Returns the URL pointing to a sound of a given category.
	 * 
	 * @param {string} category The sound category (attack, bgm, effect, mob).
	 * @param {string} name The name of the sound file.
	 * @return {string} The url to the sound file.
	 * 
	 */
	getSoundUrl(category, name) {
		return this._assetRoot + 'sound/' + category + '/' + name;
	}
}


