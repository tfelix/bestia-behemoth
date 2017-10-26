/**
 * @author       Thomas Felix
 * @copyright    2015 Thomas Felix
 */

import ko from 'knockout';
import Message from '../../io/messages/Message';
import Signal from '../../io/Signal';
import Attack from '../attack/Attack';
import ItemViewModel from '../inventory/Item';
import StatusPoint from './StatusPoints';
import StatusPointsComparer from './StatusPointsComparer';
import StatusBasedValues from './StatusBasedValues';
import ConditionValues from '.ConditionValues';

/**
 * ViewModel of a Bestia.
 * 
 * @class Bestia.Bestia
 * 
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 * @param {Object}
 *            msg - Optional. Server message object to initialize the model with
 *            values.
 * @param {Object}
 *            statusPoints - StatusPoints data object from the server to hold
 *            information about the current stats.
 * @constructor
 */
export default class Bestia {

	constructor(pubsub, msg, urlHelper) {
		if (!pubsub) {
			throw 'Bestia: PubSub must be given.';
		}
		if (!urlHelper) {
			throw 'Bestia: UrlHelper must be given.';
		}

		var self = this;

		this._pubsub = pubsub;

		this._urlHelper = urlHelper;

		this.playerBestiaId = ko.observable(0);
		this.entityId = ko.observable(0);
		this.databaseName = ko.observable('');
		this.equip = [];
		this.location = ko.observable('');
		this.posX = ko.observable(0);
		this.posY = ko.observable(0);
		this.position = ko.pureComputed(function () {
			return { x: this.posX(), y: this.posY() };
		}, this);
		this.saveLocation = ko.observable();
		this.customName = ko.observable('');
		this.sprite = ko.observable('');
		this.spriteType = ko.observable('');
		this.statusEffects = [];
		this.iconUrl = ko.pureComputed(function () {
			return urlHelper.getMobIconUrl(self.databaseName());
		});
		this.level = ko.observable(1);
		this.slot = ko.observable();

		this.statusPoints = new StatusPoint();
		this.unmodifiedStatusPoints = new StatusPoint();
		this.comparedStatusPoints = new StatusPointsComparer(this.statusPoints, this.unmodifiedStatusPoints);
		this.statusBasedValues = new StatusBasedValues();
		this.conditionValues = new ConditionValues();

		this.update(msg);
	}

	/**
	 * This method selects this bestia as active. All server updates will then
	 * forwarded to this bestia.
	 * 
	 * @method Bestia.Bestia#selectBestia
	 */
	selectBestia() {
		var activeMsg = new Message.BestiaActivate(this.entityId());
		this._pubsub.publish(Signal.IO_SEND_MESSAGE, activeMsg);
	}

	/**
	 * Updates the model with new data from the server.
	 * 
	 * @method Bestia.Bestia#update
	 * @param {Object}
	 *            msg - Message object from the server.
	 */
	update(msg) {
		if (msg === undefined) {
			return;
		}

		// Status points
		let sp = msg.sp;

		// Bestia
		let b = msg.b;

		this.playerBestiaId(b.id);
		this.entityId(msg.eid);
		this.posX(b.cl.x);
		this.posY(b.cl.y);
		this.customName(b.cn);
		this.level(b.lv);
		this.databaseName(b.b.bdbn);
		this.sprite(b.b.sp.s);
		this.spriteType(b.b.sp.t);
	}
}