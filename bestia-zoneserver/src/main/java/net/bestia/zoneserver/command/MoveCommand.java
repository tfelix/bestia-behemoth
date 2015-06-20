package net.bestia.zoneserver.command;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.BestiaMoveMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.game.zone.Vector2;

/**
 * This command is invoked as a bestia move message is received from the client.
 * The server will attempt and try to move the currently selected bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MoveCommand extends Command {

	private static final Logger log = LogManager.getLogger(MoveCommand.class);

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

		List<Vector2> path = new ArrayList<>(msg.getCordsX().size());

		// Sanity check.
		if (msg.getCordsX().size() != msg.getCordsY().size()) {
			log.warn("Paths have different length. Can not be used.");
			return;
		}

		// Create path.
		for (int i = 0; i < msg.getCordsX().size(); i++) {
			path.add(new Vector2(msg.getCordsX().get(i), msg.getCordsY().get(i)));
		}

		
	}

}
