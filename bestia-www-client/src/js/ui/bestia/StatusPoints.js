import ko from 'knockout';

/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

/**
 * ViewModel for the Status points.
 * 
 * @class Bestia.StatusPoint
 *
 * @constructor
 */
export default class StatusPoints {
	constructor(msg, curHp, curMana) {
		
		this.curMana = ko.observable(0);
		this.maxMana = ko.observable(0);
		this.curHp = ko.observable(0);
		this.maxHp = ko.observable(0);
	
		this.str = ko.observable(0);
		this.vit = ko.observable(0);
		this.int = ko.observable(0);
		this.will = ko.observable(0);
		this.agi = ko.observable(0);
		this.dex = ko.observable(0);
	
		this.defense = ko.observable(0);
		this.magicDefense = ko.observable(0);
	
		// CSS helper data.
		this.manaWidth = ko.computed(function() {
			return (this.maxMana() === 0) ? '0%' : (Math.floor(this.curMana() * 100 / this.maxMana())) + '%';
		}, this);
		this.hpWidth = ko.computed(function() {
			return (this.maxHp() === 0) ? '0%' : (Math.floor(this.curHp() * 100 / this.maxHp())) + '%';
		}, this);
	
		if (msg !== undefined) {
			this.update(msg, curHp, curMana);
		}
	}
	
	/**
	 * ViewModel for the Status points.
	 * 
	 * @method Bestia.StatusPoint
	 *#update
	 * @param {Object}
	 *            msg - Message from the server to fill the model.
	 */
	update(msg, curHp, curMana) {
		this.maxMana(msg.mmana);
		this.maxHp(msg.mhp);
		
		this.str(msg.atk);
		this.vit(msg.def);
		this.int(msg.int);
		this.will(msg.will);
		this.agi(msg.agi);
		this.dex(msg.dex);
		
		this.defense(msg.def);
		this.magicDefense(msg.mdef);
	}

	/**
	 * Updates only the status values part of this model.
	 * @param {Object} msg 
	 */
	updateValues(msg) {

		this.curHp(msg.chp);
		this.curMana(msg.cmana);
	}
}