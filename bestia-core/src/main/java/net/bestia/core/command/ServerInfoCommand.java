package net.bestia.core.command;

import java.util.ArrayList;
import java.util.List;

import net.bestia.core.game.zone.Zone;
import net.bestia.messages.Message;
import net.bestia.messages.ServerInfoMessage;
import net.bestia.util.BestiaConfiguration;

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

		final BestiaConfiguration conf = ctx.getConfiguration();
		
		List<String> zoneNames = new ArrayList<>();
		for(Zone z : ctx.getAllZones()) {
			zoneNames.add(z.getName());
		}
		
		
		final ServerInfoMessage reply = new ServerInfoMessage(message, 
				zoneNames, 
				ctx.getServer().getName(),
				ctx.getServer().getConnectedPlayer(), 
				conf.getProperty("resourceUrl"));
		ctx.getMessenger().sendMessage(reply);
	}

}
