/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

/**
 * The viewmodel of a simple bestia attack.
 * 
 * @class Bestia.BestiaAttack
 * @param {object}
 *            data - Optional: Data to initialize the attack. Otherwise default
 *            values are used.
 */
Bestia.BestiaAttack = function(data) {
	this.minLevel = ko.observable(0);
	this.id = ko.observable(0);
	this.attackDatabaseName = ko.observable('');
	this.manaCost = ko.observable(0);
	this.strength = ko.observable(0);
	this.range = ko.observable(0);
	this.basedStatus = ko.observable('');

	/**
	 * Element of this attacks.
	 * 
	 * @property {String}
	 */
	this.element = ko.observable('');

	/**
	 * Base cast time for this attack in ms. The actual cast time might be
	 * different and will be determined by the server if the attack is beeing
	 * cast.
	 * 
	 * @property {Number}
	 */
	this.casttime = ko.observable(0);

	/**
	 * Cooldown time of this attack.
	 * 
	 * @property {Number}
	 */
	this.cooldown = ko.observable(0);

	/**
	 * URL of the attack icon image on the server.
	 * 
	 * @property {String}
	 */
	this.imageUrl = ko.pureComputed(function() {
		return Bestia.Urls.assetsRoot + 'img/icons/attack/unicorn.png';
	});

	// These values must be translated via our translation service.
	this.name = ko.observable('');
	this.description = ko.observable('');

	if (data !== undefined) {
		this.update(data);
	}
};

Bestia.BestiaAttack.prototype.update = function(data) {
	// Used to accept regular attacks or lernable attacks.
	var atk = data.a || data;
	this.minLevel(data.mlv || 0);
	this.id(atk.id);
	this.attackDatabaseName(atk.adbn);
	this.strength(atk.str);
	this.element(atk.ele);
	this.manaCost(atk.m);
	this.range(atk.r);
	this.basedStatus(atk.bs);
	this.casttime(atk.ct);
	this.cooldown(atk.cd);
};