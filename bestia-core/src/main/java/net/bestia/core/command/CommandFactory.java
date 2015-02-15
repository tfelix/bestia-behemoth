package net.bestia.core.command;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import net.bestia.core.connection.BestiaConnectionInterface;
import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.message.Message;
import net.bestia.core.net.Messenger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Creates commands from incoming messages. Please bear in mind that not each
 * message creates a command for execution on the server. Only a subset of all
 * available messages have an associated command. (All the INCOMING messages).
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class CommandFactory {

	private static final Logger log = LogManager
			.getLogger(CommandFactory.class);
	
	final private static Map<String, Class<? extends Command>> commandLibrary;
	
	static {
		commandLibrary = new HashMap<String, Class<? extends Command>>();
        
		// TODO Maybe Autoregister all available commands?
		commandLibrary.put("ping", PingCommand.class);
		commandLibrary.put("req.login", RequestLoginCommand.class);
		commandLibrary.put("req.logout", RequestLogoutCommand.class);
		commandLibrary.put("chat", ChatCommand.class);
    }

	private final CommandContext context;

	/**
	 * Ctor.
	 * 
	 * @param serviceFactory
	 * @param connection
	 */
	public CommandFactory(ServiceFactory serviceFactory,
			Messenger messenger) {
		if (serviceFactory == null) {
			throw new IllegalArgumentException(
					"ServiceFactory can not be null.");
		}
		if (messenger == null) {
			throw new IllegalArgumentException(
					"Messenger can not be null.");
		}
		
		context = new CommandContext(serviceFactory, messenger);
	}

	public Command getCommand(Message message) {
		
		final String msgId = message.getMessageId();
		
		Class<? extends Command> clazz;
		clazz = commandLibrary.get(msgId);
		
		if(clazz == null) {
			log.error("Unknown command for message: {}", message.toString());
			throw new IllegalArgumentException("Unknown command for message.");
		}
		
		Command cmd = null;
		try {
			
			Constructor<? extends Command> cmdConst = clazz.getConstructor(Message.class, CommandContext.class);	
			cmd = cmdConst.newInstance(message, context);
			
		} catch (Exception e) {
			log.error("Error while creating command.", e);
			return null;
		}
		
		log.trace("Command created: {}", cmd.toString());
		return cmd;
	}
}
