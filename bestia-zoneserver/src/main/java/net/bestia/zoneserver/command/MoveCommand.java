package net.bestia.zoneserver.command;

import net.bestia.messages.BestiaMoveMessage;
import net.bestia.messages.Message;

/**
 * This command is invoked as a bestia move message is received from the client.
 * The server will attempt and try to move the currently selected bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MoveCommand extends Command {

	@Override
	public String handlesMessageId() {
		return BestiaMoveMessage.MESSAGE_ID;
	}

	/**
	 * Create a path, lookup the bestia id and then set the the path so the ECS
	 * can use it.
	 */
	@Override
	protected void execute(Message message, CommandContext ctx) {
		BestiaMoveMessage msg = (BestiaMoveMessage) message;
		
		ctx.getServer().processPlayerInput(msg);
	}

}
