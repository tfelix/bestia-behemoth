package net.bestia.zoneserver.messaging.preprocess;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.messaging.routing.MessageRouter;

/**
 * Message preprocessor implementations are used for transforming or changing
 * the messages in a certain way. Sometimes additional information of a message
 * must be gathered in order to handle it correctly. For this case the
 * {@link MessagePreprocessor}s are used.
 * <p>
 * <b>NOTE:</b> Currently the messages are checked in a chain if the
 * preprocessor is responsible. For performance gains writing an API for the
 * {@link MessagePreprocessorController} which uses a hashmap lookup for faster
 * execution might me needed. Since this is a very curcial part of the app.
 * </p>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class MessagePreprocessor {

	protected final CommandContext ctx;

	public MessagePreprocessor(CommandContext ctx) {
		if (ctx == null) {
			throw new IllegalArgumentException("CommandContext can not be null.");
		}

		this.ctx = ctx;
	}

	/**
	 * Process the given message. A modified, different message or even null
	 * (throwing this message away) can be returned. The altered message will
	 * get delivered via an {@link MessageRouter}.
	 * 
	 * @param message
	 *            Message to be processed.
	 * @return A modified or different message or null.
	 */
	public abstract Message process(Message message);

}
