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
	
	var self = this;

	/**
	 * Name of the item. Must be translated via i18n and item db name.
	 * @property
	 */
	this.name = ko.observable('');
	
	/**
	 * The type of this item.
	 */
	this.type = ko.observable();
	this.amount = ko.observable(0);
	this.playerItemId = ko.observable();
	this.itemId = ko.observable();
	this.itemDatabaseName = '';
	this.description = ko.observable('');
	this.imageSrc = ko.observable();
	this.weight = ko.observable(0);
	
	/**
	 * Total weight of the items.
	 */
	this.totalWeight = ko.pureComputed(function(){
		return self.amount() * self.weight();
	});
	
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
	
	this.type(data.i.t);
	this.amount(data.a);
	this.weight(data.i.w);
	
	this.playerItemId(data.pid);
	this.itemId(data.i.id);
	this.itemDatabaseName = data.i.idbn;
	
	this.imageSrc(Bestia.Urls.assetsItems + data.i.img);
};

/**
 * Click handler. Handles clicks on this item.
 * 
 * @public
 * @method Bestia.ItemViewModel#onClick
 */
Bestia.ItemViewModel.prototype.onClick = function() {
	
	alert("Hihi geklickt");
};
