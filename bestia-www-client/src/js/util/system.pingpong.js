/**
 * Listens for system.pong messages and displays this on the console.
 * 
 */
// Register for messages.
Bestia.subscribe('system.pong', function(_, msg) {
	console.log('Server just send a PONG! ' + JSON.stringify(msg));
});
