/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

/**
 * ItemViewModel displays an item for the user.
 * 
 * @class Bestia.ItemViewModel
 * 
 * @param {Bestia.UrlHelper}
 *            urlHelper - Helper to resolve URLs.
 */
Bestia.ItemViewModel = function(data, urlHelper) {
	
	if(!(urlHelper instanceof Bestia.UrlHelper)) {
		throw "Bestia.ItemViewModel: UrlHelper can not be null.";
	}

	var self = this;
	
	this._urlHelper = urlHelper;

	/**
	 * Name of the item. Must be translated via i18n and item db name.
	 * 
	 * @property
	 */
	this.name = ko.observable('');

	/**
	 * The type of this item.
	 */
	this.type = ko.observable('');

	/**
	 * The amount owned by the player of this item.
	 * 
	 * @property
	 * @public
	 */
	this.amount = ko.observable(0);

	/**
	 * The player item id.
	 * 
	 * @property
	 * @public
	 */
	this.playerItemId = ko.observable(0);

	/**
	 * Item id.
	 * 
	 * @property
	 * @public
	 */
	this.itemId = ko.observable(0);

	/**
	 * The unqiue database item name.
	 * 
	 * @property
	 * @public
	 */
	this.itemDatabaseName = ko.observable('');

	/**
	 * The description will be set when the item is translated by the inventory.
	 * 
	 * @property
	 * @public
	 */
	this.description = ko.observable('');

	/**
	 * The image source URL of this item. Useful for layouts.
	 * 
	 * @property
	 * @public
	 */
	this.imageSrc = ko.observable('');

	/**
	 * The weight of the item.
	 * 
	 * @property
	 * @public
	 */
	this.weight = ko.observable(0);
	
	/**
	 * Holds the indicator string of the item.
	 * 
	 * @property {string}
	 * @public
	 */
	this.indicator = "";

	
	/**
	 * Total weight of the items (by amount).
	 * 
	 * @property
	 * @public
	 */
	this.totalWeight = ko.pureComputed(function() {
		return self.amount() * self.weight();
	});

	if (data !== undefined) {
		this.update(data);
	}
};

/**
 * 
 * @public
 * @method Bestia.ItemViewModel#update
 * @param {Object}
 *            data - New itemdata from the server regarding this item.
 */
Bestia.ItemViewModel.prototype.update = function(data) {

	this.type(data.i.t);
	this.amount(data.a);
	this.weight(data.i.w);

	this.playerItemId(data.pid);
	this.itemId(data.i.id);
	this.itemDatabaseName(data.i.idbn);
	
	this.indicator = data.i.i;
	
	this.imageSrc(this._urlHelper.getItemIconUrl(data.i.img));
};
