package net.bestia.zoneserver.command;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.bestia.messages.Message;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;

/**
 * Creates commands from incoming messages. Please bear in mind that not each message creates a command for execution on
 * the server. Only a subset of all available messages have an associated command. (All the INCOMING messages).
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class CommandFactory {

	private static final Logger log = LogManager.getLogger(CommandFactory.class);

	private final CommandContext commandContext;
	private final Map<String, Command> commandLibrary = new HashMap<String, Command>();

	/**
	 * Scans the package for commands to instanciate if a message with the given ID is incoming.
	 * 
	 * @param packageName
	 */
	private void scanPackage(String packageName) {
		final Reflections reflections = new Reflections(packageName);
		final Set<Class<? extends Command>> subTypes = reflections.getSubTypesOf(Command.class);

		for (Class<? extends Command> clazz : subTypes) {
			
			// Dont instance abstract classes.
			if(Modifier.isAbstract(clazz.getModifiers())) {
				continue;
			}
			
			try {
				final Command cmd = clazz.newInstance();

				// Dont put a command handler in the library twice.
				if (commandLibrary.containsKey(cmd.handlesMessageId())) {
					log.warn("Handler for message {} already registered. Can not add command {}.",
							cmd.handlesMessageId(), clazz.toString());
					continue;
				}

				commandLibrary.put(cmd.handlesMessageId(), cmd);
			} catch (InstantiationException | IllegalAccessException e) {
				log.error("Can not instanciate command handler: {}", clazz.toString(), e);
			}
		}
	}

	/**
	 * Ctor.
	 * 
	 * @param ctx
	 *            The CommandContext to be used for executing these commands.
	 * @param packageToScan
	 *            Path to the package to be scanned for {@link Command} implementations.
	 */
	public CommandFactory(CommandContext ctx, String packageToScan) {
		if (ctx == null) {
			throw new IllegalArgumentException("Context can not be null.");
		}

		if (packageToScan == null || packageToScan.isEmpty()) {
			throw new IllegalArgumentException("PackageToScan can not be null or empty.");
		}

		this.commandContext = ctx;

		scanPackage(packageToScan);
	}

	public CommandFactory(CommandContext ctx) {
		if (ctx == null) {
			throw new IllegalArgumentException("Context can not be null.");
		}

		this.commandContext = ctx;

		scanPackage("net.bestia.zoneserver.command");
	}

	/**
	 * Creates a command from the messages. Do some sanity checking as well to see if the message is ok and applying to
	 * the specifications.
	 * 
	 * @param message
	 * @return
	 */
	public Command getCommand(Message message) {

		final String msgId = message.getMessageId();

		if (!commandLibrary.containsKey(msgId)) {
			log.error("No command found for message id: {}", msgId);
			return null;
		}

		Command cmd = commandLibrary.get(msgId);
		cmd.setCommandContext(commandContext);
		cmd.setMessage(message);

		log.trace("Command created: {}", cmd.toString());
		return cmd;
	}
}
