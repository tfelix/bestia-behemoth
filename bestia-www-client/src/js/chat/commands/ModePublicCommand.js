import RealtimeCommand from './RealtimeCommand';

/**
 * Upon entering of the command switches chat into public mode.
 */
export default class ModePublicCommand extends RealtimeCommand {
	
	constructor() {
		super();
		this.commandStr = '/s ';
	}
	
	_doCommand(text, chat) {

		chat.mode('PUBLIC');
		chat.text(text.replace('/s ', ''));
		chat.whisperNick('');
		
	}
}
