package net.bestia.core.command;

import net.bestia.core.message.Message;
import net.bestia.core.message.PingMessage;
import net.bestia.core.message.PongMessage;

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
		PongMessage msg = new PongMessage(message);
		ctx.getMessenger().sendMessage(msg);

	}

}
