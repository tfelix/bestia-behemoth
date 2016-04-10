/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import PubSub from './util/Pubsub.js';
import Config from './util/Config.js';
import UrlHelper from './util/UrlHelper.js';
import Connection from './io/connection.js';
import Signal from './io/Signal.js';
import Inventory from './inventory/Inventory.js';
import Chat from './chat/Chat.js';
import I18n from './util/I18n.js';


export const Bestia = {
	/**
	 * Bestia client version number.
	 * 
	 * @constant
	 * @type {string}
	 */
	VERSION: 'alpha-0.2.7-SNAPSHOT',
	

	/**
	 * Holds various hard coded URLs. These might be different in production and
	 * in local use. They will be replaced by the build system upon release.
	 * 
	 * @constant
	 * @property {string} Urls.loginHtml - URL of the login page.
	 * @property {string} Urls.gameHtml - URL of the main game page.
	 * @property {string} Urls.bestiaWebAPI - URL of the login API endpoint.
	 * @property {string} Urls.bestiaWebsocket - URL of the websocket API
	 *           endpoint.
	 */
	Urls : {
		// @ifdef DEVELOPMENT 
		loginHtml : 'http://localhost/login.html',
		gameHtml : 'http://localhost/index.html', 
		bestiaWebAPI : 'http://localhost:8090', 
		bestiaWebsocket: 'http://localhost:8080/api', 
		assetsRoot : 'http://localhost/assets/'
		// @endif
		
	}
};

export class BestiaGame {
	
	constructor() {
		this.pubsub = new PubSub();
		this.config = new Config(this.pubsub);
		this.i18n = new I18n(this.pubsub);
		this.urlHelper = new UrlHelper(Bestia.Urls.assetsRoot);
	
		this.inventory = new Inventory(this.pubsub, this.i18n, this.urlHelper);
		//this.bestias = new Bestia.BestiaInfoViewModel(this.pubsub, this.urlHelper);
		//this.attacks = new Bestia.BestiaAttacks(this.pubsub, this.i18n);
		
		this.chat = new Chat($('#chat'), this);
		//this.engine = new Bestia.Engine(this.pubsub, this.urlHelper);
		this.connection = new Connection(this.pubsub);
		
		var self = this;
		
		/**
		 * Start the connection process.
		 */
		this.pubsub.subscribe(Signal.IO_CONNECT, function(){
			self.connection.connect();
		});
		
		/**
		 * Disconnect from the server.
		 */
		this.pubsub.subscribe(Signal.IO_DISCONNECT, function(){
			self.connection.disconnect();
		});
		
		/**
		 * Authentication error. Go to logout.
		 */
		this.pubsub.subscribe(Signal.AUTH_ERROR, function(){
			window.location.replace(Bestia.Urls.loginHtml);
		});
	}
}
