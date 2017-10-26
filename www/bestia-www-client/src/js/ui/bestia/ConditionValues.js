import ko from 'knockout';

/**
 * @author       Thomas Felix
 * @copyright    2017 Thomas Felix
 */

/**
 * ViewModel for the condition values.
 * 
 * @class Bestia.StatusPoint
 *
 * @constructor
 */
export default class ConditionValues {
	constructor(msg) {
		
		this.curMana = ko.observable(0);
		this.maxMana = ko.observable(0);
		this.curHp = ko.observable(0);
		this.maxHp = ko.observable(0);
	
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
	update(msg) {
		this.maxMana(msg.mmana);
		this.maxHp(msg.mhp);
		this.curMana(msg.cmana);
		this.curHp(msg.chp);
	}
}