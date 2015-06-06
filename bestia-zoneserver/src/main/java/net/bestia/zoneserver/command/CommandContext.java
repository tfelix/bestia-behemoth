package net.bestia.zoneserver.command;

import java.util.Collection;
import java.util.Map;

import net.bestia.zoneserver.game.service.ServiceFactory;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.game.zone.Zone;

/**
 * Facade class to cover the needed information context for a {@link Command}
 * class. It contains references to database access and messaging API. The
 * context itself is immutable because multi-thread access will happen. The
 * member of itself must be thread safe as well.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class CommandContext {

	/**
	 * Builder pattern for creating the command context.
	 *
	 */
	public static class Builder {
		private ServiceFactory serviceFactory;
		private Map<String, Zone> zones;
		private Zoneserver zoneserver;
		private BestiaConfiguration config;

		public Builder setServiceFactory(ServiceFactory serviceFactory) {
			this.serviceFactory = serviceFactory;
			return this;
		}

		/*
		 * public Builder setMessenger(Messenger msg) { this.messenger = msg;
		 * return this; }
		 */

		public Builder setZones(Map<String, Zone> zones) {
			this.zones = zones;
			return this;
		}

		public Builder setConfiguration(BestiaConfiguration config) {
			this.config = config;
			return this;
		}

		public Builder setZoneserver(Zoneserver server) {
			this.zoneserver = server;
			return this;
		}

		public CommandContext build() {
			return new CommandContext(this);
		}
	}

	private final ServiceFactory serviceFactory;
	private final BestiaConfiguration configuration;
	private final Map<String, Zone> zones;
	private final Zoneserver server;

	/**
	 * Ctor. Creat
	 * 
	 * @param builder
	 *            Builder holding the needed variables for creating this
	 *            context.
	 */
	private CommandContext(Builder builder) {

		if (builder == null) {
			throw new IllegalArgumentException("Builder can not be null.");
		}

		if (builder.serviceFactory == null) {
			throw new IllegalArgumentException(
					"serviceFactory can not be null.");
		}

		if (builder.zones == null) {
			throw new IllegalArgumentException("zones can not be null.");
		}

		if (builder.config == null) {
			throw new IllegalArgumentException("config can not be null.");
		}

		if (builder.zoneserver == null) {
			throw new IllegalArgumentException("zoneserver can not be null.");
		}

		this.serviceFactory = builder.serviceFactory;
		this.zones = builder.zones;
		this.configuration = builder.config;
		this.server = builder.zoneserver;
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
	 * Returns the server configuration. TODO Das in ein eigenes Configuration
	 * object kapseln das immutable ist.
	 * 
	 * @return Configuration of the server.
	 */
	public BestiaConfiguration getConfiguration() {
		return configuration;
	}

	/**
	 * Return a zone by its name.
	 * 
	 * @param name
	 * @return Zone specified by its name, or NULL.
	 */
	public Zone getZone(String name) {
		return zones.get(name);
	}

	/**
	 * Returns all zones.
	 * 
	 * @return Collection of all zones.
	 */
	public Collection<Zone> getAllZones() {
		return zones.values();
	}

	/**
	 * Returns the zoneserver instance.
	 * 
	 * @return Zoneserver instance.
	 */
	public Zoneserver getServer() {
		return server;
	}

}
