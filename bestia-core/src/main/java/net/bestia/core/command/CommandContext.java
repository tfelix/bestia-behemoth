package net.bestia.core.command;

import java.util.Map;
import java.util.Properties;

import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.game.zone.Property;
import net.bestia.core.game.zone.Zone;
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
public final class CommandContext {
	
	/**
	 * Builder pattern for creating the command context.
	 *
	 */
	public static class Builder {
		private ServiceFactory serviceFactory;
		private Messenger messenger;
		private Map<String, Zone> zones;
		private Properties config;
		
		public Builder setServiceFactory(ServiceFactory serviceFactory) {
			this.serviceFactory = serviceFactory;
			return this;
		}
		
		public Builder setMessenger(Messenger msg) {
			this.messenger= msg;
			return this;
		}
		
		public Builder setZones(Map<String, Zone> zones) {
			this.zones = zones;
			return this;
		}
		
		public Builder setConfiguration(Properties config) {
			this.config = config;
			return this;
		}
		
		public CommandContext build() {
			return new CommandContext(this);
		}
	}

	private final ServiceFactory serviceFactory;
	private final Messenger messenger;
	private final Properties configuration;
	private final Map<String, Zone> zones;

	/**
	 * Ctor. Creat
	 * @param builder Builder holding the needed variables for creating this context.
	 */
	private CommandContext(Builder builder) {
		
		if(builder == null) {
			throw new IllegalArgumentException("Builder can not be null.");
		}

		this.serviceFactory = builder.serviceFactory;
		this.messenger = builder.messenger;
		this.zones = builder.zones;
		this.configuration = builder.config;
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
	
	/**
	 * Returns the server configuration. 
	 * TODO Das in ein eigenes Configuration object kapseln das immutable ist.
	 * @return Configuration of the server.
	 */
	public Properties getConfiguration() {
		return configuration;
	}
	

}
