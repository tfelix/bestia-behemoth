/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import Signal from '../io/Signal.js';
import BestiaViewModel from './BestiaViewModel.js';
import MID from '../io/messages/MID.js';
import Message from '../io/messages/Message.js';

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

		if (!pubsub) {
			throw "Bestia.BestiaInfoViewModel: Pubsub is not optional.";
		}
		if(!urlHelper) {
			throw "UrlHelper can not be null.";
		}
	
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
	
		/**
		 * Number of usable slots for bestias.
		 */
		this.slots = ko.observable(0);
	
		// Register for messages from the server.
		pubsub.subscribe(Signal.IO_CONNECTED, this._handleConnected.bind(this));
		pubsub.subscribe(Signal.IO_DISCONNECTED, this._handleDisconnected.bind(this));
		pubsub.subscribe(MID.BESTIA_INFO, this._handleOnMessage.bind(this));
		pubsub.subscribe(MID.BESTIA_ACTIVATED, this._handleOnActivate.bind(this));
	}
	
	/**
	 * Is triggered when there is a new connection established. We must ask the
	 * server for the bestias to display in here.
	 */
	_handleConnected() {
		var msg = new Message.ReqBestiaInfo();
		this._pubsub.send(msg);
	}
	
	/**
	 * When we got disconnected clear all data again.
	 */
	_handleDisconnected() {
		this.bestias.removeAll();
		this.slots(0);
		this.masterBestia(null);
	}
	
	_handleOnActivate(_, msg) {
		console.debug('New bestia was selected.');

		// Check if the bestia is already inside our cache.
		for (var i = 0; i < this.bestias().length; i++) {
			if (this.bestias()[i].playerBestiaId() === msg.pbid) {
				var bestia = this.bestias()[i];
				this._selectBestia(bestia);
			}
		}
	}
	
	/**
	 * Handler to fill out the data. We get this data from the bestia.update
	 * messages.
	 */
	_handleOnMessage(_, msg) {
		console.debug('Update bestia model with data.');

		var bestia = new BestiaViewModel(this._pubsub, msg.b, msg.sp, this._urlHelper);

		// Check if the bestia is already inside our cache.
		for (var i = 0; i < this.bestias().length; i++) {
			if (this.bestias()[i].playerBestiaId() === bestia.playerBestiaId()) {
				// Just update it.
				this.bestias()[i].update(msg.b, msg.sp);
				return;
			}
		}

		// If the bestia was not found however to the extended logic. First add
		// it.
		this.bestias.push(bestia);

		// Check if we have unselected master.
		if (this.masterBestia() === null && msg.im === true) {
			this.masterBestia(bestia);
			this._selectBestia(bestia);
		}
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
