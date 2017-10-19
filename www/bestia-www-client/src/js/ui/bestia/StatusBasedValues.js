import ko from 'knockout';

/**
 * @author       Thomas Felix 
 * @copyright    2016 Thomas Felix
 */

/**
 * ViewModel for the additional status based values.
 * 
 * @class StatusBasedValues
 *
 * @constructor
 */
export default class StatusBasedValues {
	constructor(msg) {

		/**
		 * Attackspeed
		 * @public
		 */
		this.aspd = ko.observable(0);

		/**
		 * Cast duration.
		 */
		this.cast = ko.observable(1);

		this.hpRegen = ko.observable(0);

		this.manaRegen = ko.observable(0);

		this.crit = ko.observable(0);
		this.critPerc = ko.pureComputed(function () {
			return this.makePercentage(this.crit());
		});

		this.dodge = ko.observable(0);
		this.dodgePerc = ko.pureComputed(function () {
			return this.makePercentage(this.dodge());
		});

		/**
		 * Dodge value in percent.
		 */
		this.dodgePerc = ko.pureComputed(function () {
			return this.makePercentage(this.dodge());
		}, this);

		this.casttime = ko.observable(1.0);

		this.castduration = ko.observable(1.0);

		this.hitrate = ko.observable(0);

		this.walkspeed = ko.observable(1.0);
	}

	/**
	 * Depending on the given message the object is updated with the latest modifier data from the server.
	 * @param {Object} msg 
	 */
	update(msg) {

	}

	makePercentage(value) {
		return Math.round(this.dodge() * 10) / 10 + '%';
	}
}