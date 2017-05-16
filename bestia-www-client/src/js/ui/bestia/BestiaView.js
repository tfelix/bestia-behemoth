/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import ko from 'knockout';
import Signal from '../../io/Signal.js';
import Bestia from './Bestia.js';
import MID from '../../io/messages/MID.js';
import Message from '../../io/messages/Message.js';
import LOG from '../../util/Log';

/**
 * Holds and manages a complete overview of all selected bestias.
 * 
 * @class Bestia.BestiaInfoViewModel
 * @constructor
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 * @param {Bestia.UrlHelper}
 *            urlHelper - Helper for resolving URLs.
 */
export default class BestiaView {
	
	constructor(pubsub, urlHelper) {

		if (!pubsub) {
			throw 'Bestia.BestiaInfoViewModel: Pubsub is not optional.';
		}
		if(!urlHelper) {
			throw 'UrlHelper can not be null.';
		}
	
		this._pubsub = pubsub;
	
		this._urlHelper = urlHelper;
	
		/**
		 * Holds the currently selected bestia.
		 * 
		 * @public
		 * @property {Bestia.Bestia}
		 */
		this.selectedBestia = ko.observable(null);
	
		/**
		 * Holds a reference to the master bestia.
		 * 
		 * @public
		 * @property {Bestia.Bestia}
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

		/**
		 * Entity ID of a bestia selection that has not jet been loaded.
		 * @private
		 */
		this._deferredActiveBestia = 0;
	
		// Register for messages from the server.
		pubsub.subscribe(Signal.IO_AUTH_CONNECTED, this._handleConnected.bind(this));
		pubsub.subscribe(Signal.IO_DISCONNECTED, this._handleDisconnected.bind(this));
		pubsub.subscribe(MID.BESTIA_INFO, this._handleBestiaInfo.bind(this));
		pubsub.subscribe(MID.BESTIA_ACTIVATE, this._handleOnActivate.bind(this));
		pubsub.subscribe(MID.ENTITY_POSITION, this._handlerOnPosition, this);
	}

	/**
	 * Check if we must update the position of one of our bestias.
	 */
	_handlerOnPosition(_, msg) {
		for(let i = 0; i < this.bestias().length; i++) {
			let bestia = this.bestias()[i];
			if(bestia.entityId() === msg.eid) {
				bestia.posX(msg.x);
				bestia.posY(msg.y);
			}
		}
	}
	
	/**
	 * Is triggered when there is a new connection established. We must ask the
	 * server for the bestias to display in here.
	 */
	_handleConnected() {
		LOG.debug('Requesting bestia info.');
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
	
	/**
	 * Handles the selection of the bestia from the server. If the bestia was selected before
	 * we received details about the bestia it will hold back until the data has arrived.
	 */
	_handleOnActivate(_, msg) {
		LOG.debug('New bestia was selected from server.');

		// Check if the bestia is already inside our cache.
		var bestia = this.getBestiaByEntityId(msg.eid);

		if(bestia === null) {
			this._deferredActiveBestia = msg.pbid;
			return;
		}

		this._selectBestia(bestia);
	}
	
	/**
	 * Handler to fill out the data. We get this data from the bestia.update
	 * messages.
	 */
	_handleBestiaInfo(_, msg) {
		LOG.debug('Update bestia model with data.');

		var bestia = new Bestia(this._pubsub, msg, this._urlHelper);

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

		if(this._deferredActiveBestia !== 0 && this._deferredActiveBestia === bestia.entityId()) {
			LOG.debug('Selecting deferred bestia with eid: {}', this._deferredActiveBestia);
			this._selectBestia(bestia);
		}

		// Check if we have unselected master and use a given master bestia for this.
		if (this.masterBestia() === null && msg.im === true) {
			this.masterBestia(bestia);
		}
	}
	
	/**
	 * Returns a bestia model via its entity id. 
	 */
	getBestiaByEntityId(entityId) {
		for (var i = 0; i < this.bestias().length; i++) {
			if (this.bestias()[i].entityId() === entityId) {
				return this.bestias()[i];
			}
		}

		return null;
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
