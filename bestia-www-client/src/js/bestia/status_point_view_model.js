/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

/**
 * ViewModel for the Status points.
 * 
 * @class Bestia.StatusPointViewModel
 * @constructor
 */
Bestia.StatusPointViewModel = function(msg) {
	this.curMana = ko.observable(0);
	this.maxMana = ko.observable(0);
	this.curHp = ko.observable(0);
	this.maxHp = ko.observable(0);
	this.manaWidth = ko.computed(function() {
		return (this.maxMana() === 0) ? '0%' : (Math.floor(this.curMana() * 100 / this.maxMana())) + '%';
	}, this);
	this.hpWidth = ko.computed(function() {
		return (this.maxHp() === 0) ? '0%' : (Math.floor(this.curHp() * 100 / this.maxHp())) + '%';
	}, this);

	if (msg !== undefined) {
		this.update(msg);
	}
};
/**
 * ViewModel for the Status points.
 * 
 * @method Bestia.StatusPointViewModel#update
 * @param {Object}
 *            msg - Message from the server to fill the model.
 */
Bestia.StatusPointViewModel.prototype.update = function(msg) {
	this.curMana(msg.cMana);
	this.maxMana(msg.mMana);
	this.curHp(msg.cHp);
	this.maxHp(msg.mHp);
};