/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

import Signal from '../io/Signal.js';
import PubSub from '../util/PubSub.js';

/**
 * Holds complete overview of all selected bestias.
 * 
 * @class Bestia.BestiaInfoViewModel
 * @constructor
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 * @param {Bestia.UrlHelper}
 *            urlHelper - Helper for resolving URLs.
 */
export default class BestiaInfoViewModel {
	
	constructor(pubsub, urlHelper) {

	if (!(pubsub instanceof PubSub)) {
		throw "Bestia.BestiaInfoViewModel: Pubsub is not optional.";
	}
	if(!urlHelper) {
		throw "UrlHelper can not be null.";
	}

	var self = this;

	this._pubsub = pubsub;

	this._urlHelper = urlHelper;

	/**
	 * Holds the currently selected bestia.
	 * 
	 * @public
	 * @property {Bestia.BestiaViewModel}
	 */
	this.selectedBestia = ko.observable(null);

	/**
	 * Holds a reference to the master bestia.
	 * 
	 * @public
	 * @property {Bestia.BestiaViewModel}
	 */
	this.masterBestia = ko.observable(null);

	/**
	 * Holds the currently available bestias for this bestia master.
	 * 
	 * @public
	 * @property {Array}
	 */
	this.bestias = ko.observableArray([]);

	this.slots = ko.observable(0);

	/**
	 * Handler to fill out the data. We get this data from the bestia.update
	 * messages.
	 */
	var onMessageHandler = function(_, msg) {
		console.debug('Update bestia model with data.');

		var bestia = new BestiaViewModel(self._pubsub, msg.b, msg.sp, self._urlHelper);

		// Check if the bestia is already inside our cache.
		for (var i = 0; i < self.bestias().length; i++) {
			if (self.bestias()[i].playerBestiaId() === bestia.playerBestiaId()) {
				// Just update it.
				self.bestias()[i].update(msg.b, msg.sp);
				return;
			}
		}

		// If the bestia was not found however to the extended logic. First add
		// it.
		self.bestias.push(bestia);

		// Check if we have unselected master.
		if (self.masterBestia() === null && msg.im === true) {
			self.masterBestia(bestia);
			self._selectBestia(bestia);
		}
	};

	// Register for messages from the server.
	pubsub.subscribe('bestia.info', onMessageHandler);

	var onActivateHandler = function(_, msg) {
		console.debug('New bestia was selected.');

		// Check if the bestia is already inside our cache.
		for (var i = 0; i < self.bestias().length; i++) {
			if (self.bestias()[i].playerBestiaId() === msg.pbid) {
				var bestia = self.bestias()[i];
				self._selectBestia(bestia);
			}
		}
	};
	pubsub.subscribe('bestia.activated', onActivateHandler);
	}
	

	/**
	 * Selects the given bestia and announces the selection to the world.
	 * 
	 * @param bestia
	 * @private
	 */
	_selectBestia(bestia) {
		this.selectedBestia(bestia);
		this._pubsub.publish(Signal.BESTIA_SELECTED, this.selectedBestia());
	}
}
