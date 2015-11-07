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
import net.bestia.zoneserver.manager.PlayerBestiaManager;
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

	public ECSCommandFactory(CommandContext ctx, World world, Map map) {
		super("net.bestia.zoneserver.command.ecs");

		if (ctx == null) {
			throw new IllegalArgumentException("CommandContext can not be null.");
		}

		if (world == null) {
			throw new IllegalArgumentException("World can not be null.");
		}

		this.ctx = ctx;
		this.map = map;
		this.world = world;

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

		// TODO Oh oh... hier aber lieber mal neue Instancen erzeugen...
		// Dann kann ich auch den Ctor der ECSCommands anpassen direkt den ctx
		// und msg zu bekommen.
		// These commands should be ECSCommands.
		final ECSCommand cmd = (ECSCommand) commandLibrary.get(msgId);
		cmd.setCommandContext(ctx);
		cmd.setMessage(message);

		LOG.trace("Command created: {}", cmd.toString());

		// Set the world instance.
		cmd.setWorld(world);

		if (message instanceof InputMessage) {
			final InputMessage inputMsg = (InputMessage) message;

			// Sanity check if the given account really owns this player bestia.
			final PlayerBestiaManager pbManager = playerSpawnManager
					.getPlayerBestiaManager(inputMsg.getPlayerBestiaId());
			if (pbManager == null || pbManager.getAccountId() != message.getAccountId()) {
				LOG.warn("HACKING: PlayerBestiaMessage id not consistent with account id: {}", inputMsg.toString());
				return null;
			}

			// Find the player entity for this command/message. If the bestia id
			// is invalid null should be returned.
			final int entityId = playerSpawnManager.getEntityIdFromBestia(inputMsg.getPlayerBestiaId());
			final Entity player = world.getEntity(entityId);
			cmd.setPlayer(player);
			cmd.setMap(map);
		}

		// Return the normal message.
		return cmd;
	}
}
