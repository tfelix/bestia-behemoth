'use strict';
/**
 * Main message module. Responsible for sending messages to the server and to
 * receive them and rebroadcast them into the client.
 * 
 * @module io.Connection
 */

var Bestia = window.Bestia = window.Bestia || {};
(function(app, $) {
	/**
	 * Central configuration variables.
	 */
	app.io = app.io || {};
	app.io.Connection = {

			socket : null,
			
			init : function() {
				var request = { url: document.location.protocol + "//" + document.location.host + '/behemoth',
						contentType: "application/json",
						logLevel: 'debug',
						transport: 'websocket',
						headers: {token: 'test123'},
						trackMessageLength: true,
						enableProtocol: true,
						fallbackTransport: 'long-polling'};
				
				request.onOpen = function(response) {
					console.log('Connection to established via ' + response.transport);
					$.publish('client.io.onConnected', {});
				}
				
				request.onTransportFailure = function (errorMsg, request) {
					jQuery.atmosphere.info(errorMsg);
				};
				
				request.onMessage = function (response) {
					var message = response.responseBody;
					try {
						var json = jQuery.parseJSON(message);
					} catch (e) {
						console.log('This doesn\'t look like a valid JSON: ', message.data);
						return;
					}

					// Is it a valid server message? If yes send it to the engine.
					$.publish(json.mid, json);
				};
					
				request.onClose = function (response) {
					console.log('Server has closed the connection.');
				};
				
				request.onError = function (response) {
					console.log('Server error. Can not create connection.')
				};
				
				$.publish('client.io.onConnecting', {});
				app.io.Connection.socket = $.atmosphere.subscribe(request);
			},

			sendMessage(msg) {
				// TODO Some sanity checking.
				app.io.Connection.socket.push(JSON.stringify(msg));
			}
	};

})(Bestia, jQuery);