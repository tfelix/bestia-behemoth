/**
* @author       Thomas Felix <thomas.felix@tfelix.de>
* @copyright    2015 Thomas Felix
*/

/**
 * 
 * @constructor
 * @class Bestia.Net
 * @param {Bestia.Config} config - Configuration object.
 */
Bestia.Net = function(config) {
	this.config = config;
}

/**
 * Returns the correct URL to retrieve a certain resource from the server.
 * 
 * @param {string} type - The type of the resource to request [sound, tile, sprite]
 * @param {string} name - The unique name of the resource.
 */
Bestia.Net.prototype.makeUrl = function(type, name) {
	var conf = app.server.Config;
	
	if(type == 'map') {
		return conf.resourceURL() + '/maps/' + name + '/' + name + '.json';
	} else {
		
	}
};

/**
 * Returns the resource url for an item image.
 * 
 * @method Bestia.Net#getItemImageUrl
 * @param {string} itemImg - Name of the item image to display. 
 * @returns The resource url for an item image.
 */
Bestia.Net.prototype.getItemImageUrl = function(itemImg) {
	return conf.resourceURL() + '/assets/img/items/' + item_img;
};