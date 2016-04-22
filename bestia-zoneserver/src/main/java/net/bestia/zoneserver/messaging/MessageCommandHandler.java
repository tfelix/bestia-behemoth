package net.bestia.zoneserver.messaging;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

	private final MessageProvider provider;
	private final CommandFactory commandFactory;
	private final ExecutorService commandExecutor;

	/**
	 * An external router can be provided. This can be usefull if an external
	 * one must be used for whatever reasons.
	 * 
	 * @param preprocessor
	 * @param commandFactory
	 * @param messageRouter
	 */
	public MessageCommandHandler(int numThreads, CommandFactory commandFactory, MessageProvider provider) {

		this.commandExecutor = Executors.newFixedThreadPool(numThreads);
		this.commandFactory = commandFactory;
		this.provider = provider;

		// Register all message ids which the factory can handle to the router.
		final Set<String> messageIDs = commandFactory.getRegisteredMessageIds();

		// Subscribe for all ids.
		messageIDs.forEach(id -> provider.subscribe(id, this));
	}

	@Override
	public void handleMessage(Message msg) {

		final Command cmd = commandFactory.getCommand(msg);

		if (cmd != null) {
			commandExecutor.submit(cmd);
		}
	}

	/**
	 * Cleanly shutdown the messaging loop.
	 */
	public void shutdown() {
		// Unsubscribe.
		final Set<String> messageIDs = commandFactory.getRegisteredMessageIds();
		messageIDs.forEach(id -> provider.unsubscribe(id, this));

		commandExecutor.shutdown();
	}

}
