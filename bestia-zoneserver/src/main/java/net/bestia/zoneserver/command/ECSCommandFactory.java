package net.bestia.zoneserver.command;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.managers.TagManager;

import net.bestia.messages.InputMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.command.ecs.ECSCommand;

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

	private final TagManager tagManager;

	public ECSCommandFactory(CommandContext ctx, World world) {
		super("netb.bestia.zoneserver.command.ecs");

		if (ctx == null) {
			throw new IllegalArgumentException("CommandContext can not be null.");
		}

		if (world == null) {
			throw new IllegalArgumentException("World can not be null.");
		}

		this.ctx = ctx;
		this.world = world;

		this.tagManager = world.getSystem(TagManager.class);
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
		// Dann kann ich auch den Ctor der ECSCommands anpassen direkt den ctx und msg zu bekommen.
		// These commands should be ECSCommands.
		final ECSCommand cmd = (ECSCommand) commandLibrary.get(msgId);
		cmd.setCommandContext(ctx);
		cmd.setMessage(message);

		LOG.trace("Command created: {}", cmd.toString());

		// Set the world instance.
		cmd.setWorld(world);

		if (message instanceof InputMessage) {
			// Find the player entity for this command/message. If the bestia id
			// is
			// invalid null should be returned.
			final Entity player = tagManager.getEntity(Integer.toString(((InputMessage) message).getPlayerBestiaId()));
			cmd.setPlayer(player);
		}

		// Return the normal message.
		return cmd;
	}
}
