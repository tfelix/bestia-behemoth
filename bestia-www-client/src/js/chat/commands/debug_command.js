/**
 * Toggles between the debug mode of the game.
 * 
 * Usage: /debug ON|OFF
 * 
 * @returns TRUE if the chat string starts with this command. FALSE otherwise.
 */
/*
Bestia.Chat.localCommands.push(function(chat, game, input) {
	
	var cmdStr = /\/debug (ON|OFF)/gi;
	
	if(!input.match(cmdStr)) {
		return false;
	}

	if(RegExp.$1.toUpperCase() === 'ON') {
		game.config.debug(true);
	} else {
		game.config.debug(false);
	}

	return true;
});*/