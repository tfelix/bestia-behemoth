import NOOP from '../util/NOOP.js';
import LOG from '../util/Log';
import { engineContext } from './EngineData';

/**
 * This class is responsible for loading the description files of entities. It
 * will determine from the context in which URL it needs to look. All loading
 * operations are backed by the DemandLoader and thus are asynchronous.
 */
export default class DescriptionLoader {
	constructor() {

		this._loader = engineContext.loader;
		this._url = engineContext.url;
	}

	/**
	 * Returns the description JSON file if it has already been loaded. Null
	 * otherwise. The Name can be either the key name of the description file or
	 * the data object. The DescriptionLoader will try to determine its name
	 * then from the data object.
	 * 
	 * @param {String}
	 *            name - The name of the sprite/visual description file.
	 */
	getDescription(name) {
		if ((typeof name) !== 'string') {
			name = this._getNameFromData(name) + '_desc';
		}

		return this._loader.get(name, 'json');
	}

	/**
	 * Loads the description file from the server. When the description was
	 * loaded the callback function will be executed. It will get the
	 * description file object as an argument.
	 * 
	 * @param {object}
	 *            data - Object describing the description.
	 * @param {function}
	 *            fnCallback - Callback function. Executed when the data was
	 *            loaded.
	 */
	loadDescription(data, fnCallback) {
		fnCallback = fnCallback || NOOP;

		var url = this._getUrlFromData(data);
		var name = this._getNameFromData(data) + '_desc';

		LOG.debug('Loading description: ' + name + ' from: ' + url);

		this._loader.load({
			key: name,
			type: 'json',
			url: url
		}, function () {
			LOG.debug('Description loaded: ' + name + ' from: ' + url);

			var descFile = this.getDescription(name);
			try {
				fnCallback(descFile);
			} catch (err) {
				LOG.error('Error while executing callback after loaded description: ' + JSON.stringify(descFile), err);
			}

		}.bind(this));
	}

	/**
	 * Returns the right description URL depending on the data type.
	 */
	_getUrlFromData(data) {

		switch (data.sprite.type.toUpperCase()) {
			case 'PACK':
			case 'DYNAMIC':
				// its an mob.
				return this._url.getMobDescUrl(data.sprite.name);
			default:
				// its an object.
				return this._url.getObjectDescUrl(data.sprite.name);
		}
	}

	/**
	 * Returns the sprite name from the given data object.
	 * 
	 * @param {object}
	 *            data
	 */
	_getNameFromData(data) {
		return data.sprite.name;
	}
}