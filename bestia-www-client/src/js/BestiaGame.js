/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import Authenticator from './io/Authenticator';
import Connection from './io/Connection.js';
import Engine from './engine/Engine.js';


export default class BestiaGame {
	
	constructor(pubsub, urlHelper) {
		
		//this.i18n = new I18n(this.pubsub);
		this.auth = new Authenticator(pubsub);
		this.engine = new Engine(pubsub, urlHelper);
		this.connection = new Connection(pubsub);
		
	}
}
