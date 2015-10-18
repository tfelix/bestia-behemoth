/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

/**
 * Attack view model will show and display attacks.
 * 
 * @class Bestia.AttackViewModel
 */
Bestia.AttackViewModel = function(data) {

	// var self = this;

	/**
	 * ID of the attack.
	 * 
	 * @property
	 */
	this.id = ko.observable(0);

	this.attackDatabaseName = ko.observable();

	this.strength = ko.observable();

	this.element = ko.observable();

	this.manaCost = ko.observable();

	this.range = ko.observable();

	this.basedStatus = ko.observable();

	this.casttime = ko.observable();

	this.cooldown = ko.observable();

	/**
	 * Name of the item. Must be translated via i18n and item db name.
	 * 
	 * @property
	 */
	this.name = ko.observable('');

	if (data !== undefined) {
		this.update(data);
	}
};

/**
 * 
 * @public
 * @method Bestia.AttackViewModel#update
 * @param {Object}
 *            data - New attack data from the server.
 */
Bestia.ItemViewModel.prototype.update = function(data) {

	this.id(data.id);
	this.attackDatabaseName(data.adbn);
	this.strengh(data.str);
	this.element(data.ele);
	this.manaCost(data.m);
	this.range(data.r);
	this.basedStatus(data.bs);
	this.casttime(data.ct);
	this.cooldown(data.cd);
};
