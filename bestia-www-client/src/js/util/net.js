/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */
(function(Bestia) {
	'use strict';
	/**
	 * 
	 * @constructor
	 * @class Bestia.Net
	 * @param {Bestia.Config}
	 *            config - Configuration object.
	 */
	Bestia.Net = function(config) {
		this.config = config;
	};

	/**
	 * Returns the correct URL to retrieve a certain resource from the server.
	 * 
	 * @param {string}
	 *            type - The type of the resource to request [sound, tile,
	 *            sprite]
	 * @param {string}
	 *            name - The unique name of the resource.
	 */
	Bestia.Net.prototype.makeUrl = function(type, name) {
		if (type == 'map') {
			return this.config.resourceURL() + '/maps/' + name + '/' + name + '.json';
		}
	};

	/**
	 * Returns the resource url for an item image.
	 * 
	 * @method Bestia.Net#getItemImageUrl
	 * @param {string}
	 *            itemImg - Name of the item image to display.
	 * @returns The resource url for an item image.
	 */
	Bestia.Net.prototype.getItemImageUrl = function(itemImg) {
		return this.config.resourceURL() + '/img/items/' + itemImg;
	};

	/**
	 * Returns the resource object for a mob sprite. This is not so easy since a
	 * mob has many resources associated with it. First of all it has a sprite,
	 * a detailed image and maybe JSON files describing more information about
	 * animation etc.
	 * 
	 * The returned object looks like this: {spriteSheet: URL, spriteInfo: URL,
	 * img: URL}
	 * 
	 * @method Bestia.Net#getItemImageUrl
	 * @param {string}
	 *            dbName - Database unique name of the bestia.
	 * @returns {Object} Object with information regarding this mob.
	 */
	Bestia.Net.prototype.getMobImageUrl = function(dbName) {
		var obj = {
			spriteSheet : '',
			spriteInfo : '',
			img : this.config.resourceURL() + '/mob/' + dbName + '.png'
		};

		return obj;
	};

	/**
	 * Returns the url to fetch item translation.
	 * 
	 * @method Bestia.Net#getItemI18NUrl
	 * @param {Number}
	 *            itemId - Id of the item to fetch.
	 * @returns {Object} - JSON object of the item translation.
	 */
	Bestia.Net.prototype.getItemI18NUrl = function(itemId) {
		return this.config.resourceURL() + '/i18n/' + this.config.locale() + '/item/' + itemId;
	};
})(Bestia);