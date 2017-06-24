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

		this.maxManaCompare = 0;
		this.maxMana = ko.pureComputed(function(){
			this._getText(this.maxMana);
		});

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

	_getText(val) {
		if(val === 0) {
			return val;
		}
		return (val > 0) ? '+' + val : '-' + val;
	}

	/**
	 * Compares the modified and unmodified values with each other.
	 */
	_compare() {
		// Does only compare if both values are not null.
		if(this._unmodPoints === null || this._points === null) {
			return;
		}

		this.maxManaCompare(this._points.maxMana() - this._unmodPoints.maxMana());
		this.maxHpCompare(this._points.maxHp() - this._unmodPoints.maxHp());

		this.strCompare(this._points.str() - this._unmodPoints.str());
		this.vitCompare(this._points.vit() - this._unmodPoints.vit());
		this.intCompare(this._points.int() - this._unmodPoints.int());
		this.willCompare(this._points.will() - this._unmodPoints.will());
		this.agiCompare(this._points.agi() - this._unmodPoints.agi());
		this.dexCompare(this._points.dex() - this._unmodPoints.dex());

		this.defenseCompare(this._points.defense() - this._unmodPoints.defense());
		this.magicDefenseCompare(this._points.magicDefense() - this._unmodPoints.magicDefense());
	}

	/**
	 * Sets the unmodified status points.
	 */
	setUnmodifiedPoints(unmodifiedPoints) {
		if (unmodifiedPoints === undefined) {
			throw 'Can not set unmodifiedPoints to undefined.';
		}
		this._unmodPoints = unmodifiedPoints;
		this._compare();
	}

	/**
	 * Sets the modified status points.
	 */
	setPoints(statusPoints) {
		if (statusPoints === undefined) {
			throw 'Can not set statusPoints to undefined.';
		}

		this._points = statusPoints;
		this._compare();
	}
}