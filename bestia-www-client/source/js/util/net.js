/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
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
		return this.config.resourceURL() + '/assets/img/items/' + itemImg;
	};
	
	/**
	 * Returns the url to fetch item translation.
	 * 
	 * @method Bestia.Net#getItemI18NUrl
	 * @param {Number} itemId - Id of the item to fetch.
	 * @returns {Object} - JSON object of the item translation.
	 */
	Bestia.Net.prototype.getItemI18NUrl = function(itemId) {
		return this.config.resourceURL() + '/assets/i18n/'+this.config.locale()+'/item/' + itemId;
	};
})(Bestia);