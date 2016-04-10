import BasicCommand from './BasicCommand.js';

/**
 * Switches to chat whisper mode with the given username.
 */
export default class ModeWhisperCommand extends BasicCommand {
	constructor() {
		super();
		this._whisperRegex = /^\/[wW] (\w.+) /;
	}
	
	isCommand(text, chat) {

		if (this._whisperRegex.test(text)) {
			chat.whisperNick(RegExp.$1);
			chat.text(text.replace(this._whisperRegex, ''));
		}

	}
}
