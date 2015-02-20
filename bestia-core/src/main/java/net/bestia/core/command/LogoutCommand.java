package net.bestia.core.command;

import net.bestia.core.message.LogoutMessage;
import net.bestia.core.message.Message;

class LogoutCommand extends Command {

	@Override
	public void execute(Message message, CommandContext ctx) {
		LogoutMessage msg = new LogoutMessage(message);
		ctx.getMessenger().sendMessage(msg);
	}

	@Override
	public String handlesMessageId() {
		return LogoutMessage.MESSAGE_ID;
	}

}
