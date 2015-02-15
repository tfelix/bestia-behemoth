package net.bestia.core.game.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.core.message.Message;
import net.bestia.core.net.Messenger;

/**
 * Abstract base class for all services. Provides facility to send out
 * updates if a change to the underlying data has occured.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class Service {
	private final static Logger log = LogManager.getLogger(Service.class);
	
	private Messenger messenger;
	
	/**
	 * Ctor.
	 * @param queue Message queue so the services can deliver their very own messages.
	 */
	public Service(Messenger messenger) {
		if(messenger == null) {
			throw new IllegalArgumentException("Messenger can not be null.");
		}
		this.messenger = messenger;
	}
	
	/**
	 * Clients must implement this method it should generate a message for the client
	 * to update its information in case of changed data.
	 * @return
	 */
	protected abstract Message getDataChangedMessage();
	
	/**
	 * Implementations should call this method if some operations have changed
	 * the data managed by this service. It will then issue a update message 
	 * to the client.
	 */
	protected void onChange() {
		Message msg = getDataChangedMessage();
		log.trace("Underlying data changed. Issued message: {}", msg);
		messenger.sendMessage(getDataChangedMessage());
	}
}
