package net.bestia.zoneserver.command;

import net.bestia.messages.LogoutMessage;
import net.bestia.messages.Message;

class LogoutCommand extends Command {

	@Override
	public void execute(Message message, CommandContext ctx) {
		LogoutMessage msg = new LogoutMessage(message);
		//ctx.getMessenger().sendMessage(msg);
	}

	@Override
	public String handlesMessageId() {
		return LogoutMessage.MESSAGE_ID;
	}

}
