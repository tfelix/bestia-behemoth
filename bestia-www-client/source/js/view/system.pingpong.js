'use strict';
/**
 * Listens for system.pong messages and displays this on the console.
 * 
 * @module Server.Config
 */
(function($) {
	
	// Register for messages.
	$.subscribe('system.pong', function(_, msg) {
		console.log('Server just send a PONG! ' + JSON.stringify(msg));
	});
	
})(jQuery);