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
		var message = JSON.stringify(msg);
		console.trace('Sending Message: ' + message);
		self.socket.push(message);
	});
};

/**
 * Checks if logindata is ok or otherwise not complete.
 * 
 * @private
 * @param {Object}
 *            data - The data object containting the login data.
 * @method Bestia.Connection#checkLoginData
 * @returns TRUE if all the login data is existing. FALSE if there is something
 *          missing or an error.
 */
Bestia.Connection.prototype.checkLoginData = function(data) {
	
	var state = true;
	
	if (!data) {
		state = false;
	}
	
	if(!state || data.token === undefined) {
		console.error("Login: token missing.");
		state = false;
	} else if(!state | data.accId === undefined) {
		console.error("Login:account id missing.");
		state = false;
	} else if(!state | data.username === undefined) {
		console.error("Login: username missing.");
		state = false;
	}
	
	if(state === false) {
		window.location.replace(Bestia.Urls.loginHtml);
		return false;
	}
	
	return true;
};

/**
 * 
 * Publishes: auth
 */
Bestia.Connection.prototype.init = function() {

	// Prepare the request.
	var store = new Bestia.Storage();
	var authData = store.getAuth();

	this.checkLoginData(authData);

	// Emit the auth data signal so other parts of the app can react to it.
	Bestia.publish('system.auth', authData);

	var request = {
		url : Bestia.Urls.bestiaWebsocket,
		contentType : "application/json",
		logLevel : 'debug',
		transport : 'websocket',
		headers : {
			'X-Bestia-Token' : authData.token,
			'X-Bestia-Account' : authData.accId
		},
		maxReconnectOnClose : 0,
		trackMessageLength : true,
		enableProtocol : true
	};

	request.onOpen = function(response) {
		console.log('Connection to established via ' + response.transport);
		Bestia.publish('io.onConnected', {});
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

		console.debug('Received Message: ' + message);

		try {
			var json = jQuery.parseJSON(message);
			// Is it a valid server message? If yes send it to the engine.
			Bestia.publish(json.mid, json);
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
