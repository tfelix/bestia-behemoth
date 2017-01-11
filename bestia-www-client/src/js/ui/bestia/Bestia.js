/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

import Message from '../../io/messages/Message';
import Signal from '../../io/Signal';
import Attack from '../../attack/Attack';
import ItemViewModel from '../../inventory/Item';
import StatusPoint from './StatusPoints';

/**
 * ViewModel of a Bestia.
 * 
 * @class Bestia.BestiaViewModel
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
export default class BestiaViewModel {
	
	constructor(pubsub, msg, urlHelper) {
		if (!pubsub) {
			throw 'BestiaViewModel: PubSub must be given.';
		}
		if (!urlHelper) {
			throw 'BestiaViewModel: UrlHelper must be given.';
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
		this.saveLocation = ko.observable();
		this.customName = ko.observable('');
		this.sprite = ko.observable('');
		this.spriteType = ko.observable('');
		this.statusEffects = [];
		this.iconUrl = ko.pureComputed(function() {
			return urlHelper.getMobIconUrl(self.databaseName());
		});
		this.level = ko.observable(1);
		this.slot = ko.observable();
		this.statusPoints = new StatusPoint();
		
		// TODO Das hier noch ins system einbinden.
		this.walkspeed = ko.observable(1.0);
	
		this.attack1 = ko.observable(null);
		this.attack2 = ko.observable(null);
		this.attack3 = ko.observable(null);
		this.attack4 = ko.observable(null);
		this.attack5 = ko.observable(null);
	
		this.item1 = ko.observable(null);
		this.item2 = ko.observable(null);
		this.item3 = ko.observable(null);
		this.item4 = ko.observable(null);
		this.item5 = ko.observable(null);
	
		this.update(msg);
	}
	
	/**
	 * This method selects this bestia as active. All server updates will then
	 * forwarded to this bestia.
	 * 
	 * @method Bestia.BestiaViewModel#selectBestia
	 */
	selectBestia() {
		var activeMsg = new Message.BestiaActivate(this.playerBestiaId());
		this._pubsub.publish(Signal.IO_SEND_MESSAGE, activeMsg);
	}
	

	/**
	 * Updates the model with new data from the server.
	 * 
	 * @method Bestia.BestiaViewModel#update
	 * @param {Object}
	 *            msg - Message object from the server.
	 */
	update(msg) {
		if (msg !== undefined) {
			
			// Status points
			let sp = msg.sp;
			
			// Bestia
			let b = msg.b;
			
			this.playerBestiaId(b.id);
			this.entityId(msg.eid);
			this.location(b.cl.mdbn);
			this.posX(b.cl.x);
			this.posY(b.cl.y);
			this.saveLocation(b.sl.mdbn);
			this.customName(b.cn);
			this.level(b.lv);
			this.databaseName(b.b.bdbn);
			this.sprite(b.b.sp.s);
			this.spriteType(b.b.sp.t);
			// this.statusEffects = [];
			// this.slot(msg.sl);

			// Update the attacks.
			if (b.atk1) {
				this.attack1(new Attack(b.atk1));
			}

			if (b.atk2) {
				this.attack2(new Attack(b.atk2));
			}

			if (b.atk3) {
				this.attack3(new Attack(b.atk3));
			}

			if (b.atk4) {
				this.attack4(new Attack(b.atk4));
			}

			if (b.atk5) {
				this.attack5(new Attack(b.atk5));
			}

			// Update the items.
			if (b.item1) {
				this.item1(new ItemViewModel(b.item1, this._urlHelper));
			}

			if (b.item2) {
				this.item2(new ItemViewModel(b.item2, this._urlHelper));
			}

			if (b.item3) {
				this.item3(new ItemViewModel(b.item3, this._urlHelper));
			}

			if (b.item4) {
				this.item4(new ItemViewModel(b.item4, this._urlHelper));
			}

			if (b.item5) {
				this.item5(new ItemViewModel(b.item5, this._urlHelper));
			}
			
			this.statusPoints.update(sp);
		}
	}
}