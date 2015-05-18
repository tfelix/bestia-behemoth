/**
 * Clears the chat when the command gets executed.
 * 
 * Usage: /clear
 * 
 * @returns TRUE if the chat string starts with this command. FALSE otherwise.
 */
Bestia.Chat.localCommands.push(function(chat, game, input) {

	if (!strStartsWith(input, '/clear')) {
		return false;
	}

	chat.messages.removeAll();
	chat.text('');

	return true;
});