/**
 * @author       Thomas Felix
 * @copyright    2015 Thomas Felix
 */

import ko from 'knockout';
import Message from '../../io/messages/Message';
import Signal from '../../io/Signal';
import StatusPoint from './StatusPoints';
import StatusBasedValues from './StatusBasedValues';
import ConditionValues from './ConditionValues';
import ComponentNames from '../../engine/entities/ComponentNames';

/**
 * Viewmodel for a Bestia.
 * 
 * @export
 * @class Bestia
 */
export default class Bestia {

	/**
	 * Creates an instance of Bestia.
	 * @param {any} eid Entity ID of this bestia.
	 * @param {any} pubsub 
	 * @param {any} urlHelper 
	 * @memberof Bestia
	 */
	constructor(eid, pubsub, urlHelper) {
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
		this.entityId = ko.observable(eid);
		
		this.databaseName = ko.observable('');
		this.customName = ko.observable('');

		this.sprite = ko.observable('');
		this.spriteType = ko.observable('');
		this.iconUrl = ko.pureComputed(function () {
			return urlHelper.getMobIconUrl(self.databaseName());
		});

		this.equip = [];
		
		this.location = ko.observable('');
		this.posX = ko.observable(0);
		this.posY = ko.observable(0);
		this.position = ko.pureComputed(function () {
			return { x: this.posX(), y: this.posY() };
		}, this);
		this.saveLocation = ko.observable();
		
		this.statusEffects = [];
		
		this.level = ko.observable(1);
		this.slot = ko.observable();

		this.statusPoints = new StatusPoint();
		this.originalStatusPoints = new StatusPoint();
		this.statusBasedValues = new StatusBasedValues();
		this.conditionValues = new ConditionValues();
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
	 * @param {Object} entity - Message object from the server.
	 */
	update(entity) {
		if (!entity) {
			return;
		}

		this.customName("HelloWorld");

		// PLAYER COMPONENT
		if(entity.components.hasOwnProperty(ComponentNames.PLAYER)) {
			
		}

		// Check if status is present.
		if(entity.components.hasOwnProperty(ComponentNames.STATUS)) {
			let statComp = entity.components[ComponentNames.STATUS];
			this.statusPoints.update(statComp.statusPoints);
			this.originalStatusPoints.update(statComp.originalStatusPoints);
			this.conditionValues.update(statComp.conditionValues);
			this.statusBasedValues.update(statComp.statusBasedValues);
		}

		if(entity.components.hasOwnProperty(ComponentNames.VISIBLE)) {
			let comp = entity.components[ComponentNames.VISIBLE];
			this.sprite(comp.visual.sprite);
			this.spriteType(comp.visual.type);
		}

		// Bestia
		//let b = msg.b;

		/*
		this.playerBestiaId(b.id);
		this.entityId(msg.eid);
		this.posX(b.cl.x);
		this.posY(b.cl.y);
		this.customName(b.cn);
		this.level(b.lv);
		this.databaseName(b.b.bdbn);
		this.sprite(b.b.sp.s);
		this.spriteType(b.b.sp.t);*/
	}
}