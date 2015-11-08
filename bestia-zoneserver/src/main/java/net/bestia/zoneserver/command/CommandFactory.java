package net.bestia.zoneserver.command;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;

import net.bestia.messages.Message;

public abstract class CommandFactory {

	private static final Logger LOG = LogManager.getLogger(CommandFactory.class);
	protected final Map<String, Command> commandLibrary = new HashMap<String, Command>();

	/**
	 * Scans the package for commands to instanciate if a message with the given
	 * ID is incoming.
	 * 
	 * @param packageName
	 */
	private void scanPackage(String packageName) {
		final Reflections reflections = new Reflections(packageName);
		final Set<Class<? extends Command>> subTypes = reflections.getSubTypesOf(Command.class);

		for (Class<? extends Command> clazz : subTypes) {

			// Dont instance abstract classes.
			if (Modifier.isAbstract(clazz.getModifiers())) {
				continue;
			}

			try {
				final Command cmd = clazz.newInstance();

				// Dont put a command handler in the library twice.
				if (commandLibrary.containsKey(cmd.handlesMessageId())) {
					LOG.warn("Handler for message {} already registered. Can not add command {}.",
							cmd.handlesMessageId(), clazz.toString());
					continue;
				}

				commandLibrary.put(cmd.handlesMessageId(), cmd);
			} catch (InstantiationException | IllegalAccessException e) {
				LOG.error("Can not instanciate command handler: {}", clazz.toString(), e);
			}
		}
	}

	public CommandFactory(String packageToScan) {
		if (packageToScan == null || packageToScan.isEmpty()) {
			throw new IllegalArgumentException("PackageToScan can not be null or empty.");
		}
		scanPackage(packageToScan);
	}

	/**
	 * Creates a set of message IDs for which this factory will create commands.
	 * 
	 * @return The registered message ids for which commands will be returned.
	 */
	public Set<String> getRegisteredMessageIds() {
		return new HashSet<String>(commandLibrary.keySet());
	}

	/**
	 * Creates a command from the messages. Do some sanity checking as well to
	 * see if the message is ok and applying to the specifications.
	 * 
	 * @param message
	 * @return
	 */
	public abstract Command getCommand(Message message);

}