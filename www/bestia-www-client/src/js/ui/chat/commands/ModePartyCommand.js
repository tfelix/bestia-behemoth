import RealtimeCommand from './RealtimeCommand';

/**
 * Switches the chat mode to party.
 */
export default class ModePartyCommand extends RealtimeCommand {
	
	constructor() {
		super();
		this.commandStr = '/p ';
	}
	
	_doCommand(text, chat) {

		chat.mode('PARTY');
		chat.text(text.replace(this.commandStr, ''));
		chat.whisperNick('');
		
	}
}