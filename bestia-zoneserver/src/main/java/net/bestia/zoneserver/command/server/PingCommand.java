package net.bestia.zoneserver.command.server;

import net.bestia.messages.Message;
import net.bestia.messages.PingMessage;
import net.bestia.messages.PongMessage;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;

/**
 * Creates a Pong message which is echoed to the client. This is the answer of a
 * ping message.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
class PingCommand extends Command {

	@Override
	public String toString() {
		return "PingCommand[]";
	}

	@Override
	public String handlesMessageId() {
		return PingMessage.MESSAGE_ID;
	}

	@Override
	public void execute(Message message, CommandContext ctx) {
		// Nothing is done here. Just a message will be returned.
		final PongMessage msg = new PongMessage(message);
		ctx.getServer().sendMessage(msg);
	}

}
