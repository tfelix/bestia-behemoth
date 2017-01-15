import NOOP from '../../util/NOOP.js';

/**
 * This class is responsible for loading the description files of entities. It
 * will determine from the context in which URL it needs to look. All loading
 * operations are backed by the DemandLoader and thus are asynchronous.
 */
export default class DescriptionLoader {
	constructor(ctx) {
		
		this._loader = ctx.loader;
		this._url = ctx.url;
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
		if((typeof name) !== 'string') {
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

		this._loader.load({
			key : name,
			type : 'json',
			url : url
		}, function() {
			var descFile = this.getDescription(name);
			fnCallback(descFile);
		}.bind(this));
	}

	/**
	 * Returns the right description URL depending on the data type.
	 */
	_getUrlFromData(data) {

		switch (data.t.toUpperCase()) {
		case 'PACK':
		case 'DYNAMIC':
			// its an mob.
			return this._url.getMobDescUrl(data.s);
		default:
			// its an object.
			return this._url.getObjectDescUrl(data.s);
		}
	}

	/**
	 * Returns the sprite name from the given data object.
	 * 
	 * @param {object}
	 *            data
	 */
	_getNameFromData(data) {
		return data.s;
	}
}