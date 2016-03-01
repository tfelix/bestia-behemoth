package net.bestia.zoneserver.messaging.preprocess;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.util.PackageLoader;

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

	private final List<MessagePreprocessor> preprocessors = new ArrayList<>();

	public MessagePreprocessorController(CommandContext commandContext) {

		// Automatically add the class instancing MessageProprocessor.
		final PackageLoader<MessagePreprocessor> loader = new PackageLoader<>(MessagePreprocessor.class,
				"net.bestia.zoneserver.messaging.preprocess");
		
		final Set<Class<? extends MessagePreprocessor>> clazzes = loader.getSubClasses();
		for(Class<? extends MessagePreprocessor> clazz : clazzes) {
			try {
				final MessagePreprocessor mp = clazz.getDeclaredConstructor(CommandContext.class).newInstance(commandContext);
				addProcessor(mp);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				LOG.error("Could not automagically add instance of {}", clazz.toGenericString(), e);
			}
		}

		//addProcessor(new ChatMessagePreprocessor(commandContext));
		//addProcessor(new LoginBroadcastMessagePreprocessor(commandContext));
		//addProcessor(new LogoutBroadcastMessagePreprocessor(commandContext));
	}

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
		if (message == null) {
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
