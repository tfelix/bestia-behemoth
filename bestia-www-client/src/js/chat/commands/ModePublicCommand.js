import BasicCommand from './BasicCommand.js';

export default class ModePublicCommand extends BasicCommand {
	
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
