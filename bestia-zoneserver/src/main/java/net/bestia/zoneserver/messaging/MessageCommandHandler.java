package net.bestia.zoneserver.messaging;

import java.util.Set;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandFactory;

/**
 * This handler statically takes and {@link CommandFactory} which turns messages
 * into commands and executed them in parallel via an {@link ExecutorService}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MessageCommandHandler implements MessageHandler {

	private final CommandFactory commandFactory;

	/**
	 * An external router can be provided. This can be usefull if an external
	 * one must be used for whatever reasons.
	 * 
	 * @param preprocessor
	 * @param commandFactory
	 * @param messageRouter
	 */
	public MessageCommandHandler(CommandFactory commandFactory, MessageProvider provider) {

		this.commandFactory = commandFactory;

		// Register all message ids which the factory can handle to the router.
		final Set<String> messageIDs = commandFactory.getRegisteredMessageIds();

		// Subscribe for all ids.
		messageIDs.forEach(id -> provider.subscribe(id, this));
	}

	@Override
	public void handleMessage(Message msg) {

		final Command cmd = commandFactory.getCommand(msg);

		if (cmd != null) {
			cmd.run();
		}
	}
}
