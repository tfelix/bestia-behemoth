import RealtimeCommand from './RealtimeCommand';

/**
 * Switches the chat to guild chat mode.
 */
export default class ModeGuildCommand extends RealtimeCommand {
	
	constructor() {
		super();
		this.commandStr = '/g ';
	}
	
	_doCommand(text, chat) {

		chat.mode('GUILD');
		chat.text(text.replace(this.commandStr, ''));
		chat.whisperNick('');
		
	}
}