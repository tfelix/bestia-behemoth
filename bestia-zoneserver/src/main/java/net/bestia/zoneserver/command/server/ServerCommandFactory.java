package net.bestia.zoneserver.command.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.command.CommandFactory;

/**
 * Creates commands from incoming messages. Please bear in mind that not each message creates a command for execution on
 * the server. Only a subset of all available messages have an associated command. (All the INCOMING messages).
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ServerCommandFactory extends CommandFactory {

	private static final Logger log = LogManager.getLogger(ServerCommandFactory.class);

	private final CommandContext commandContext;


	/**
	 * Ctor.
	 * 
	 * @param ctx
	 *            The CommandContext to be used for executing these commands.
	 * @param packageToScan
	 *            Path to the package to be scanned for {@link Command} implementations.
	 */
	public ServerCommandFactory(CommandContext ctx, String packageToScan) {
		super(packageToScan);
		if (ctx == null) {
			throw new IllegalArgumentException("Context can not be null.");
		}

		this.commandContext = ctx;
	}

	public ServerCommandFactory(CommandContext ctx) {
		super("net.bestia.zoneserver.command");
		if (ctx == null) {
			throw new IllegalArgumentException("Context can not be null.");
		}

		this.commandContext = ctx;
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.command.CommandFactoryInterf#getCommand(net.bestia.messages.Message)
	 */
	@Override
	public Command getCommand(Message message) {

		final String msgId = message.getMessageId();

		if (!commandLibrary.containsKey(msgId)) {
			log.error("No command found for message id: {}", msgId);
			return null;
		}

		final Command cmd = commandLibrary.get(msgId);
		cmd.setCommandContext(commandContext);
		cmd.setMessage(message);

		log.trace("Command created: {}", cmd.toString());
		return cmd;
	}
}
