import RealtimeCommand from './RealtimeCommand';

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
