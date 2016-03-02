package net.bestia.zoneserver.messaging;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandFactory;
import net.bestia.zoneserver.messaging.preprocess.MessagePreprocessor;
import net.bestia.zoneserver.messaging.routing.MessageFilter;
import net.bestia.zoneserver.messaging.routing.MessageIdFilter;
import net.bestia.zoneserver.messaging.routing.MessageRouter;

public class MessageLoop implements MessageHandler {

	private final MessagePreprocessor preprocessor;
	private final MessageRouter router;
	private final CommandFactory commandFactory;
	private final ExecutorService commandExecutor = Executors.newFixedThreadPool(1);

	/**
	 * Internal handler. Handles the messages directly for the command factory
	 * in this loop.
	 */
	private class ZoneserverMessageProcessor implements MessageHandler {

		@Override
		public void handleMessage(Message msg) {
			final Command cmd = commandFactory.getCommand(msg);
			if (cmd != null) {
				commandExecutor.submit(cmd);
			}
		}
	}

	public MessageLoop(MessagePreprocessor preprocessor) {
		this.preprocessor = preprocessor;
		this.commandFactory = null;
		this.router = new MessageRouter();
	}

	/**
	 * An external router can be provided. This can be usefull if an external
	 * one must be used for whatever reasons.
	 * 
	 * @param preprocessor
	 * @param commandFactory
	 * @param messageRouter
	 */
	public MessageLoop(MessagePreprocessor preprocessor, CommandFactory commandFactory, MessageRouter messageRouter) {

		this.preprocessor = preprocessor;
		this.commandFactory = commandFactory;
		this.router = messageRouter;

		// Register all message ids which the factory can handle to the router.
		final Set<String> messageIDs = commandFactory.getRegisteredMessageIds();
		final MessageFilter filter = new MessageIdFilter(messageIDs);
		router.registerFilter(filter, new ZoneserverMessageProcessor());
	}

	@Override
	public void handleMessage(Message msg) {
		// Preprocess the message.
		msg = preprocessor.process(msg);

		if (msg == null) {
			// Msg was not intended for this server.
			return;
		}

		// Route the message.
		router.handleMessage(msg);
	}

	/**
	 * The {@link MessageRouter} is exposed so it is possible to add new message
	 * filter to the routing of this message loop.
	 * 
	 * @return
	 */
	public MessageRouter getRouter() {
		return router;
	}

	/**
	 * Cleanly shutdown the messaging loop.
	 */
	public void shutdown() {
		commandExecutor.shutdown();
	}

}
