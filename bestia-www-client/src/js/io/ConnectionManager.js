
/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */


/**
 * The connection manager will keep track about the connection state and it is
 * responsible for sending the correct events when the engine starts up or a
 * server connections is unexpectedly lost. He will orchestrate all connection
 * attempts. If an error condition happens or too many connection attempts fail
 * the error handling is done by this class.
 * Internally it is basically a state machine.
 * 
 * @class Bestia.Connection
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 */
export default class ConnectionManager {
	
	/**
	 * Ctor.
	 * 
	 * @constructor
	 */
	constructor(pubsub) {

		this._pubsub = pubsub;
		
	}
}

