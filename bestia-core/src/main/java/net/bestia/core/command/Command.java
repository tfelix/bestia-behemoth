package net.bestia.core.command;

import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.core.game.service.AccountService;
import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.message.Message;

/**
 * This class represents the very basic interface for commands which are
 * executed by the bestia gameserver. Commands are created out of messages from
 * the clients. All necessairy informations are included into the base class so
 * the retrieved child classes can do their work. By using the static methods of
 * the command factory it is possible to create other commands which will be
 * issued by the bestia game server.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
public abstract class Command implements Runnable {

	private static final Logger log = LogManager.getLogger(Command.class);
	
	protected enum PreExecutionCheck {
		OK,
		FAILED
	}

	protected ServiceFactory serviceFactory;
	protected AccountService sender;
	private BlockingQueue<Message> queue;
	private Message message;	

	/**
	 * Ctor. Creates a basic command object.
	 * 
	 * @param account
	 */
	public Command(Message message, 
			ServiceFactory serviceFactory, 
			BlockingQueue<Message> queue) {
		if (serviceFactory == null) {
			throw new IllegalArgumentException("ServiceFactory can not be null.");
		}
		if(queue == null) {
			throw new IllegalArgumentException("Queue can not be null.");
		}
		if(message == null) {
			throw new IllegalArgumentException("Message can not be null.");
		}
		this.serviceFactory = serviceFactory;
		this.queue = queue;
		this.message = message;
	}

	/**
	 * Checks if the Command is valid in the given context and can be executed.
	 * If this is not the case because some precondition dont match anymore the
	 * execution will not be proceeded. An error code will be returned.
	 * 
	 * @return Error code, 0 means OK proceed with execution.
	 */
	protected abstract PreExecutionCheck validateExecution();
	
	public void run() {
		if(validateExecution() == PreExecutionCheck.FAILED) {
			// Command is no valid anymore. Stop execution.
			return;
		}
		setupAccountService(message);
		// Execute sub-class logic.
		executeCommand();
	}
	
	/**
	 * Finds the sender of the command/message and puts it to the source of the
	 * command. This is a extra method so in case the a command needs another method
	 * to identify a account it can override this method here.
	 * @param message
	 */
	protected void setupAccountService(Message message) {
		sender = serviceFactory.getAccountServiceFactory()
				.getAccount(message.getAccountId());
	}
	
	protected abstract void executeCommand();
	
	protected void addMessage(Message msg) {
		if(msg.getAccountId() == 0) {
			log.warn("Message ID was not set! {0}", msg.toString());
		}
		log.trace("Adding message: {0}", msg.toString());
		queue.add(msg);
	}
}
