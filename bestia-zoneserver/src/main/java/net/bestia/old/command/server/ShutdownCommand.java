package net.bestia.zoneserver.command.server;

import net.bestia.messages.Message;
import net.bestia.messages.system.ShutdownMessage;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;

/**
 * Stops the server when this messages was received.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ShutdownCommand extends Command {

	@Override
	public String handlesMessageId() {
		return ShutdownMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		
		ctx.getServer().stop();
		
	}

}
