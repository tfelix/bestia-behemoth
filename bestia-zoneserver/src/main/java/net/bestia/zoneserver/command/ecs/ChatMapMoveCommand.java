package net.bestia.zoneserver.command.ecs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.MapMoveMessage;
import net.bestia.messages.Message;
import net.bestia.model.domain.Location;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

public class ChatMapMoveCommand extends ECSCommand {

	private static final Logger LOG = LogManager.getLogger(ChatMapMoveCommand.class);

	@Override
	public String handlesMessageId() {
		return MapMoveMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		final MapMoveMessage msg = (MapMoveMessage) message;

		final PlayerBestiaSpawnManager pbsManager = world.getSystem(PlayerBestiaSpawnManager.class);
		final PlayerBestiaManager pbManager = pbsManager.getPlayerBestiaManager(msg.getPlayerBestiaId());
		
		final Location target = msg.getTarget();

		if (msg.getTarget().getMapDbName().isEmpty()) {
			// Just move x and y.
			LOG.info("GM-Command: MAP_MOVE, pb: {}, to: {}", pbManager.toString(), target.toString());
			
			pbManager.getLocation().setX(target.getX());
			pbManager.getLocation().setY(target.getY());
		} else {
			// Set on different map.

			LOG.warn("Moving between maps not yet implemented.");

		}
	}

}
