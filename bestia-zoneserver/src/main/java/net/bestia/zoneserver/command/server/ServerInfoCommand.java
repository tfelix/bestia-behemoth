package net.bestia.zoneserver.command.server;

import java.util.Set;

import net.bestia.messages.Message;
import net.bestia.messages.ServerInfoMessage;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;

/**
 * Returns information about this zone server which is requested and needed for
 * the client in order to start operation.
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

		final BestiaConfiguration conf = ctx.getConfiguration();

		final Set<String> zoneNames = ctx.getServer().getResponsibleZones();

		final ServerInfoMessage reply = new ServerInfoMessage((ServerInfoMessage) message,
				zoneNames, ctx.getServer().getName(), 0,
				conf.getProperty("resourceUrl"));
		ctx.getServer().sendMessage(reply);
	}

}
