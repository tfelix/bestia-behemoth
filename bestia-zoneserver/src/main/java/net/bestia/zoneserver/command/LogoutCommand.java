package net.bestia.zoneserver.command;

import net.bestia.messages.LogoutBroadcastMessage;
import net.bestia.messages.Message;

class LogoutCommand extends Command {

	@Override
	public void execute(Message message, CommandContext ctx) {
		LogoutBroadcastMessage msg = new LogoutBroadcastMessage(message);
		//ctx.getMessenger().sendMessage(msg);
	}

	@Override
	public String handlesMessageId() {
		return LogoutBroadcastMessage.MESSAGE_ID;
	}

}
