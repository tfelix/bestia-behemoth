/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

/**
 * ItemViewModel displays an item for the user.
 * 
 * @class Bestia.ItemViewModel
 */
Bestia.ItemViewModel = function(data) {

	/**
	 * Name of the item. Must be translated via i18n and item db name.
	 * @property
	 */
	this.name = ko.observable();
	this.type = ko.observable();
	this.amount = ko.observable(0);
	this.playerItemId = ko.observable();
	this.itemId = ko.observable();
	this.itemDatabaseName = '';
	this.image = ko.observable();
	
	if(data !== undefined) {
		this.update(data);
	}
};

/**
 * 
 * @public
 * @method Bestia.ItemViewModel#update
 * @param {Object} data - New itemdata from the server regarding this item.
 */
Bestia.ItemViewModel.prototype.update = function(data) {
	
	//this.name(data.idbn);
	
	this.type(data.i.t);
	this.amount(data.a);
	
	this.playerItemId(data.pid);
	this.itemId(data.i.id);
	this.itemDatabaseName = data.i.idbn;
	
	this.image(Bestia.Urls.assetsItems + data.i.img);
};
