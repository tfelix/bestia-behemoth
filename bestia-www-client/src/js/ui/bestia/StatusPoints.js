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
export default class StatusPointViewModel {
	constructor(msg) {
		this.curMana = ko.observable(0);
		this.maxMana = ko.observable(0);
		this.curHp = ko.observable(0);
		this.maxHp = ko.observable(0);
	
		this.atk = ko.observable(0);
		this.def = ko.observable(0);
		this.spAtk = ko.observable(0);
		this.spDef = ko.observable(0);
		this.spd = ko.observable(0);
	
		this.armor = ko.observable(0);
		this.spArmor = ko.observable(0);
	
		// CSS helper data.
		this.manaWidth = ko.computed(function() {
			return (this.maxMana() === 0) ? '0%' : (Math.floor(this.curMana() * 100 / this.maxMana())) + '%';
		}, this);
		this.hpWidth = ko.computed(function() {
			return (this.maxHp() === 0) ? '0%' : (Math.floor(this.curHp() * 100 / this.maxHp())) + '%';
		}, this);
	
		if (msg !== undefined) {
			this.update(msg);
		}
	}
	
	/**
	 * ViewModel for the Status points.
	 * 
	 * @method Bestia.StatusPointViewModel#update
	 * @param {Object}
	 *            msg - Message from the server to fill the model.
	 */
	update(msg) {
		this.curMana(msg.cmana);
		this.maxMana(msg.mmana);
		this.curHp(msg.chp);
		this.maxHp(msg.mhp);
		
		this.atk(msg.atk);
		this.def(msg.def);
		this.spAtk(msg.spatk);
		this.spDef(msg.spdef);
		this.spd(msg.spd);
		
		this.armor(msg.adef);
		this.spArmor(msg.spdef);
	}
}