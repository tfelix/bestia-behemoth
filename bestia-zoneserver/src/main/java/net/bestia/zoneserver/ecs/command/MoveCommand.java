package net.bestia.zoneserver.ecs.command;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.BestiaMoveMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.zone.Vector2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This command is invoked as a bestia move message is received from the client.
 * The server will attempt and try to move the currently selected bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MoveCommand extends ECSCommand {
	
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
		
		final BestiaMoveMessage msg = (BestiaMoveMessage) message;
		
		if (msg.getCordsX().size() != msg.getCordsY().size()) {
			log.warn("Size of the path array not equal in length: {}", msg);
			return;
		}

		// Convert the strange JSON format to a path array.
		final List<Vector2> path = new ArrayList<>(msg.getCordsX().size());

		for (int i = 0; i < msg.getCordsX().size(); i++) {
			path.add(new Vector2(msg.getCordsX().get(i), msg.getCordsY().get(i)));
		}

		Movement movement = player.edit().create(Movement.class);
		movement.path.addAll(path);
	}
	
	@Override
	public String toString() {
		return "MoveCommand[]";
	}
}
