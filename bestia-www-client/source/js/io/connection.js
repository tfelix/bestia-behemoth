/**
 * Main message module. Responsible for sending messages to the server and to
 * receive them and rebroadcast them into the client.
 * 
 * @module io.Connection
 */

var Bestia = window.Bestia = window.Bestia || {};
(function(app, $) {
	'use strict';
	 var Connection = {

		socket : null,

		init : function() {
			var request = {
				url : 'http://localhost:8080/api', // document.location.protocol
													// + "//" +
													// document.location.host +
													// '/api',
				contentType : "application/json",
				logLevel : 'debug',
				transport : 'websocket',
				headers : {
					bestia_token : 'test-1234-1234-1234',
					bestia_acc_id : 1
				},
				trackMessageLength : true,
				enableProtocol : true,
				fallbackTransport : 'long-polling'
			};

			request.onOpen = function(response) {
				console.log('Connection to established via ' + response.transport);
				$.publish('io.onConnected', {});
			};

			request.onTransportFailure = function(errorMsg, request) {
				console.log('Error while failing transport: ' + errorMsg);
				jQuery.atmosphere.info(errorMsg);
			};

			/**
			 * Handle an incoming message from the server. The message is
			 * filtered, parsed to JSON and then delivered to the pub-sub
			 * mechanism.
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
				} catch (e) {
					console.log('No valid JSON: ', message.data);
					return;
				}

				// Is it a valid server message? If yes send it to the engine.
				$.publish(json.mid, json);
			};

			/**
			 * Handle the close event.
			 */
			request.onClose = function(response) {
				console.log('Server has closed the connection.');
			};

			/**
			 * Handle the error event.
			 */
			request.onError = function(response) {
				console.error('Server error. Can not create connection.');
			};

			$.publish('io.onConnecting', {});
			app.io.Connection.socket = $.atmosphere.subscribe(request);
		},

		sendPing : function() {
			app.io.Connection.socket.push(JSON.stringify({
				mid : 'system.ping',
				m : 'Hello Bestia.'
			}));
		},
		
		// Sends a message while listening to this channel.
		$.publish('io.sendMessage', function(_, msg) {
			app.io.Connection.socket.push(JSON.stringify(msg));
		});
	};

})(Bestia, jQuery);