/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import PubSub from './util/Pubsub.js';
import Config from './util/Config.js';
import UrlHelper from './util/UrlHelper.js';
import Urls from './Urls.js';
import Connection from './io/connection.js';
import Signal from './io/Signal.js';
import Inventory from './inventory/Inventory.js';
import Chat from './chat/Chat.js';
import I18n from './util/I18n.js';
import BestiaAttacks from './attack/BestiaAttacks.js';
import BestiaInfoViewModel from './bestia/BestiaInfoViewModel.js';
import Engine from './engine/Engine.js';


export default class BestiaGame {
	
	constructor() {
		this.pubsub = new PubSub();
		this.config = new Config(this.pubsub);
		this.i18n = new I18n(this.pubsub);
		this.urlHelper = new UrlHelper(Urls.assetsRoot);
	
		this.inventory = new Inventory(this.pubsub, this.i18n, this.urlHelper);
		this.bestias = new BestiaInfoViewModel(this.pubsub, this.urlHelper);
		this.attacks = new BestiaAttacks(this.pubsub, this.i18n, this.urlHelper);
		
		this.chat = new Chat($('#chat'), this, this.i18n);
		this.engine = new Engine(this.pubsub, this.urlHelper);
		this.connection = new Connection(this.pubsub);
		
		this.pubsub.subscribe(Signal.IO_CONNECTED, function(){
			this.connection.sendPing();
		}.bind(this));
		
		
		/**
		 * Authentication error. Go to logout.
		 */
		this.pubsub.subscribe(Signal.AUTH_ERROR, function(){
			window.location.replace(Urls.loginHtml);
		});
	}
}
