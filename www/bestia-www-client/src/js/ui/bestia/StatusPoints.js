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
	constructor(msg) {
	
		this.str = ko.observable(0);
		this.vit = ko.observable(0);
		this.int = ko.observable(0);
		this.will = ko.observable(0);
		this.agi = ko.observable(0);
		this.dex = ko.observable(0);
	
		this.defense = ko.observable(0);
		this.magicDefense = ko.observable(0);

		if (msg !== undefined) {
			this.update(msg);
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
		
		this.str(msg.atk);
		this.vit(msg.def);
		this.int(msg.int);
		this.will(msg.will);
		this.agi(msg.agi);
		this.dex(msg.dex);
		
		this.defense(msg.def);
		this.magicDefense(msg.mdef);
	}
}