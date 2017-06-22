import ko from 'knockout';

/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2016 Thomas Felix
 */

/**
 * ViewModel for the Status point comparer. This one conpares two sets of status 
 * points and will show the differences between the status points. Can be used 
 * to visualize the differences to the user.
 * 
 * @class Bestia.StatusPoint
 *
 * @constructor
 */
export default class StatusPointsComparer {

	constructor() {

		this._unmodPoints = null;
		this._points = null;

		this.maxManaCompare = ko.observable(0);
		this.maxHpCompare = ko.observable(0);

		this.strCompare = ko.observable(0);
		this.vitCompare = ko.observable(0);
		this.intCompare = ko.observable(0);
		this.willCompare = ko.observable(0);
		this.agiCompare = ko.observable(0);
		this.dexCompare = ko.observable(0);

		this.defenseCompare = ko.observable(0);
		this.magicDefenseCompare = ko.observable(0);

		this._compare();
	}

	/**
	 * Compares the modified and unmodified values with each other.
	 */
	_compare() {
		// Does only compare if both values are not null.
		if(this._unmodPoints === null || this._points === null) {
			return;
		}

		this.maxManaCompare(this._unmodPoints.maxMana() - this._points.maxMana());
	}

	/**
	 * Sets the unmodified status points.
	 */
	setUnmodifiedPoints(unmodifiedPoints) {
		if (unmodifiedPoints === undefined) {
			throw 'Can not set unmodifiedPoints to undefined.';
		}
		this._unmodPoints = unmodifiedPoints;
	}

	/**
	 * Sets the modified status points.
	 */
	setPoints(statusPoints) {
		if (statusPoints === undefined) {
			throw 'Can not set statusPoints to undefined.';
		}

		this._points = statusPoints;
	}
}