package net.bestia.core.command;

import java.util.ArrayList;
import java.util.Properties;

import net.bestia.core.message.Message;
import net.bestia.core.message.ServerInfoMessage;

/**
 * Returns information about this zone server which is requested and needed for the client in order to start operation.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ServerInfoCommand extends Command {

	@Override
	public String handlesMessageId() {
		return ServerInfoMessage.MESSAGE_ID;
	}

	@Override
	public void execute(Message message, CommandContext ctx) {

		Properties conf = ctx.getConfiguration();
		
		final ServerInfoMessage reply = new ServerInfoMessage(message, new ArrayList<String>(), 1, "http:localhost/resource");
		ctx.getMessenger().sendMessage(reply);
	}

}
