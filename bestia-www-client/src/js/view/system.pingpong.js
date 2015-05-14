/**
 * Listens for system.pong messages and displays this on the console.
 * 
 * @module Server.Config
 */
(function(Bestia) {
	'use strict';
	// Register for messages.
	Bestia.PubSub.subscribe('system.pong', function(_, msg) {
		console.log('Server just send a PONG! ' + JSON.stringify(msg));
	});

})(Bestia);