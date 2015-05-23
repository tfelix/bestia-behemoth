/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

/**
 * Main message module. Responsible for sending messages to the server and to
 * receive them and rebroadcast them into the client.
 * 
 * @class Bestia.Connection
 */
Bestia.Connection = function() {
	var self = this;
	this.socket = null;

	// Sends a message while listening to this channel.
	Bestia.subscribe('io.sendMessage', function(_, msg) {
		self.socket.push(JSON.stringify(msg));
	});
};

Bestia.Connection.prototype.init = function() {
	var request = {
		url : 'http://localhost:8080/api',
		contentType : "application/json",
		logLevel : 'debug',
		transport : 'websocket',
		headers : {
			'bestia_token' : 'test-1234-1234-1234',
			'bestia_acc_id' : 1
		},
		maxReconnectOnClose: 0,
		trackMessageLength : true,
		enableProtocol : true
	};

	request.onOpen = function(response) {
		console.log('Connection to established via ' + response.transport);
		Bestia.PubSub.publish('io.onConnected', {});
	};

	request.onTransportFailure = function(errorMsg) {
		console.log('Error while failing transport: ' + errorMsg);
		jQuery.atmosphere.info(errorMsg);
	};

	/**
	 * Handle an incoming message from the server. The message is filtered,
	 * parsed to JSON and then delivered to the pub-sub mechanism.
	 */
	request.onMessage = function(response) {
		var message = response.responseBody;

		// Ignore keepalive.
		if (message == 'X') {
			return;
		}

		console.log('Message: ' + message);

		try {
			var json = jQuery.parseJSON(message);
			// Is it a valid server message? If yes send it to the engine.
			Bestia.PubSub.publish(json.mid, json);
		} catch (e) {
			console.log('No valid JSON: ', message.data);
			return;
		}
	};

	/**
	 * Handle the close event.
	 */
	request.onClose = function() {
		console.log('Server has closed the connection.');
	};

	/**
	 * Handle the error event.
	 */
	request.onError = function() {
		console.error('Server error. Can not create connection.');
	};

	Bestia.publish('io.onConnecting', {});
	this.socket = $.atmosphere.subscribe(request);
};

Bestia.Connection.prototype.sendPing = function() {
	this.socket.push(JSON.stringify({
		mid : 'system.ping',
		m : 'Hello Bestia.'
	}));
};
