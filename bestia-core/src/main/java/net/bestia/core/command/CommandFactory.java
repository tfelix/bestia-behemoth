package net.bestia.core.command;

import java.util.concurrent.BlockingQueue;

import net.bestia.core.connection.BestiaConnectionManager;
import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.message.Message;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Creates commands from incoming messages. Please bear in mind that not each message
 * creates a command for execution on the server. Only a subset of all available messages
 * have an associated command. (All the INCOMING messages).
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class CommandFactory {

	private static final Logger log = LogManager.getLogger(CommandFactory.class);
	
	private ServiceFactory serviceFactory;
	private BestiaConnectionManager connection;
	private BlockingQueue<Message> msgOutQueue;

	/**
	 * Ctor.
	 * 
	 * @param serviceFactory
	 * @param msgOutQueue
	 * @param connection 
	 */
	public CommandFactory(ServiceFactory serviceFactory, 
			BlockingQueue<Message> msgOutQueue,
			BestiaConnectionManager connection) {
		if(serviceFactory == null) {
			throw new IllegalArgumentException("ServiceFactory can not be null.");
		}
		if(msgOutQueue == null) {
			throw new IllegalArgumentException("OutMessageQueue can not be null.");
		}
		if(connection == null) {
			throw new IllegalArgumentException("BestiaConnectionManager can not be null.");
		}
		this.connection = connection;
		this.msgOutQueue = msgOutQueue;
		this.serviceFactory = serviceFactory;
	}

	public Command getCommand(Message message) {

		int msgId = message.getMessageId();
		Command cmd = null;

		// TODO Die commands haben jetzt einheitlichen ctor. Instanzierung kann automatisiert
		// werden. Commands müssen sich bei der factory registrieren.
		switch (msgId) {
		case 1:
			cmd = new PingCommand(message, serviceFactory, msgOutQueue);
			break;
		case 100:
			cmd = new RequestLoginCommand(message, serviceFactory, msgOutQueue, connection);
			break;
		case 102:
			cmd = new RequestLogoutCommand(message, serviceFactory, msgOutQueue);
			break;
		case 201:
			cmd = new ChatCommand(message, serviceFactory, msgOutQueue);
		default:
			log.error("Unknown command for message: {}", message.toString());
			throw new IllegalArgumentException("Unknown command for message.");
		}
		
		// Some commands need special attention. 
		// E.g. special values like the connection manager.
		
		
		log.trace("Command created: {}", cmd.toString());

		return cmd;
	}
}
