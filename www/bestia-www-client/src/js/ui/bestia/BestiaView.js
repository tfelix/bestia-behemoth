/**
 * @author Thomas Felix
 * @copyright 2017 Thomas Felix
 */

import ko from 'knockout';
import Signal from '../../io/Signal.js';
import Bestia from './Bestia.js';
import MID from '../../io/messages/MID.js';
import Message from '../../io/messages/Message.js';
import LOG from '../../util/Log';

/**
 * Holds the views to all bestias.
 * 
 * @export
 * @class BestiaView
 */
export default class BestiaView {

	constructor(pubsub, urlHelper) {

		if (!pubsub) {
			throw 'Bestia.BestiaInfoViewModel: Pubsub is not optional.';
		}
		if (!urlHelper) {
			throw 'UrlHelper can not be null.';
		}

		this._pubsub = pubsub;

		this._urlHelper = urlHelper;

		this._masterEntityId = 0;
		this._playerBestiasEids = [];

		this._entityMessageBuffer = [];

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

		// Register for messages from the server.
		pubsub.subscribe(Signal.IO_AUTH_CONNECTED, this._handleConnected.bind(this));
		pubsub.subscribe(Signal.IO_DISCONNECTED, this._handleDisconnected.bind(this));
		pubsub.subscribe(MID.BESTIA_INFO, this._handleBestiaInfo.bind(this));
		pubsub.subscribe(MID.BESTIA_ACTIVATE, this._handleOnActivate.bind(this));
		pubsub.subscribe(Signal.ENTITY_UPDATE, this._handleEntityUpdated, this);
	}

	/**
	 * Check if we must update the position of one of our bestias.
	 */
	_handleEntityUpdated(_, entity) {

		// Buffer messages as long as we dont know which are our entities.
		if(this._masterEntityId === 0) {
			this._entityMessageBuffer.push(entity);
			return;
		}

		// Only use if the component is for your bestia.
		let bestia = this.getBestiaByEntityId(entity.eid);
		if(bestia) {
			bestia.update(entity);
		}

		// Check if we must select the master bestia.
		if(this.selectedBestia() === null && bestia.entityId() === this._masterEntityId) {
			this._selectBestia(bestia);
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
		this.selectedBestia(null);
	}

	/**
	 * Handles the selection of the bestia from the server. If the bestia was selected before
	 * we received details about the bestia it will hold back until the data has arrived.
	 */
	_handleOnActivate(_, msg) {
		LOG.debug('New bestia was selected from server.');

		// Check if the bestia is already inside our cache.
		var bestia = this.getBestiaByEntityId(msg.eid);
		this._selectBestia(bestia);
	}

	/**
	 * Handler to fill out the data. We get this data from the bestia.update
	 * messages.
	 */
	_handleBestiaInfo(_, msg) {
		LOG.debug('Update bestia model with data: ' + JSON.stringify(msg));

		this._masterEntityId = msg.m;
		this._playerBestiasEids = msg.bestiaEids;

		// Clear all bestias.
		this.bestias.removeAll();

		this._playerBestiasEids.forEach(eid => {
			let bestia = new Bestia(eid, this._pubsub, this._urlHelper);
			this.bestias.push(bestia);
		});

		// Check the buffer.
		if(this._entityMessageBuffer.length > 0) {
			this._entityMessageBuffer.forEach(msg => {
				this._handleEntityUpdated('', msg);
			});
			this._entityMessageBuffer = [];
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
		this._pubsub.publish(Signal.BESTIA_SELECTED, bestia);
	}
}
