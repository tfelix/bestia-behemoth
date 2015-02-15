package net.bestia.core.command;

import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.net.Messenger;

/**
 * Facade class to cover the needed information context for a {@link Command}
 * class. It contains references to database access and messaging API. The
 * context itself is immutable because multi-thread access will happen. The
 * member of itself must be thread safe as well.
 * 
 * @author Thomas
 *
 */
final class CommandContext {

	private final ServiceFactory serviceFactory;
	private final Messenger messenger;

	public CommandContext(
			ServiceFactory serviceFactory, 
			Messenger messenger) {
		
		if(serviceFactory == null) {
			throw new IllegalArgumentException("ServiceFactory can not be null.");
		}
		
		if(messenger == null) {
			throw new IllegalArgumentException("Messenger can not be null.");
		}

		this.serviceFactory = serviceFactory;
		this.messenger = messenger;
	}

	/**
	 * Returns the {@link ServiceFactory}.
	 * 
	 * @return ServiceFactory to create DAOs.
	 */
	public ServiceFactory getServiceFactory() {
		return serviceFactory;
	}

	/**
	 * Returns the {@link Messenger} to send messages to connected clients.
	 * 
	 * @return
	 */
	public Messenger getMessenger() {
		return messenger;
	}

}
