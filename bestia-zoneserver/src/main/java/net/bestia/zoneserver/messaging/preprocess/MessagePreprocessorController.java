package net.bestia.zoneserver.messaging.preprocess;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.Message;

/**
 * The message routing is a very complex topic. Some message need more
 * information to be delivered directly to the systems where it needs to be. We
 * will install several preprocessors which will execute different tasks. All
 * messages will be piped through every preprocessor. The messages can be
 * altered, or exchanged with different messages. The can also be dropped.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MessagePreprocessorController {

	private static final Logger LOG = LogManager.getLogger(MessagePreprocessorController.class);

	private List<MessagePreprocessor> preprocessors = new ArrayList<>();

	/**
	 * Adds a new preprocessor to the controller.
	 * 
	 * @param processor
	 */
	public void addProcessor(MessagePreprocessor processor) {
		preprocessors.add(processor);
	}

	/**
	 * Preprocess the incoming message. If null is returned the message got
	 * thrown out.
	 * 
	 * @param message
	 * @return
	 */
	public Message preprocess(Message message) {
		if(message == null) {
			throw new IllegalArgumentException("Message can not be null.");
		}
		LOG.trace("Preprocessing: {}", message.toString());

		for (MessagePreprocessor p : preprocessors) {
			message = p.process(message);
			if (message == null) {
				return null;
			}
		}
		return message;
	}

}
