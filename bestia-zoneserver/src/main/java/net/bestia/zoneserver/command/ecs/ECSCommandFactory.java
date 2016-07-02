package net.bestia.zoneserver.command.ecs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Entity;
import com.artemis.World;

import net.bestia.messages.InputMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.command.CommandFactory;
import net.bestia.zoneserver.command.server.ServerCommandFactory;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.zone.Zone;
import net.bestia.zoneserver.zone.map.Map;

/**
 * This {@link ServerCommandFactory} will look into the type of the message. If
 * it is an {@link InputMessage} the message will be directed to the ECS via an
 * {@link InputCommand}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ECSCommandFactory extends CommandFactory {

	private final static Logger LOG = LogManager.getLogger(ECSCommandFactory.class);

	private final CommandContext ctx;
	private final World world;
	private final Map map;
	private final PlayerBestiaSpawnManager playerSpawnManager;
	private final Zone zone;

	public ECSCommandFactory(CommandContext ctx, World world, Map map, Zone zone) {
		super("net.bestia.zoneserver.command.ecs");

		if (ctx == null) {
			throw new IllegalArgumentException("CommandContext can not be null.");
		}

		if (world == null) {
			throw new IllegalArgumentException("World can not be null.");
		}

		if (zone == null) {
			throw new IllegalArgumentException("Zone can not be null.");
		}

		this.ctx = ctx;
		this.map = map;
		this.world = world;
		this.zone = zone;

		this.playerSpawnManager = world.getSystem(PlayerBestiaSpawnManager.class);
	}

	/**
	 * Returns a InputCommand if the message was a {@link InputMessage}.
	 * 
	 */
	@Override
	public Command getCommand(Message message) {

		final String msgId = message.getMessageId();

		if (!commandLibrary.containsKey(msgId)) {
			LOG.error("No command found for message id: {}", msgId);
			return null;
		}

		// These commands should be ECSCommands.
		final ECSCommand cmd = (ECSCommand) commandLibrary.get(msgId);
		cmd.setCommandContext(ctx);
		cmd.setMessage(message);

		LOG.trace("Command created: {}", cmd.toString());

		// Set the world instance.
		cmd.setWorld(world);
		cmd.setZone(zone);
		cmd.setMap(map);

		if (message instanceof InputMessage) {
			final InputMessage inputMsg = (InputMessage) message;

			final int pbid;

			// Use the best bestia ID. If none is given use the active bestia.
			if (inputMsg.getPlayerBestiaId() == 0) {
				pbid = ctx.getAccountRegistry().getActiveBestia(inputMsg.getAccountId());
			} else {
				pbid = inputMsg.getPlayerBestiaId();
			}

			// Find the player entity for this command/message. If the bestia id
			// is invalid null should be returned.
			final int entityId = playerSpawnManager.getEntityIdFromBestia(pbid);

			if (entityId == 0) {
				return cmd;
			}

			final Entity player = world.getEntity(entityId);
			cmd.setPlayer(player);
		}

		// Return the normal message.
		return cmd;
	}
}
