package net.bestia.zoneserver.command;

import net.bestia.model.ServiceLocator;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.Zoneserver;

/**
 * Facade class to cover the needed information context for a {@link Command}
 * class. It contains references to database access and messaging API. The
 * context itself is immutable because multi-thread access will happen. All the
 * member of this class must therefore be thread safe as well.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class CommandContext {

	private final BestiaConfiguration configuration;
	private final Zoneserver server;
	private final ServiceLocator serviceLocator;

	/**
	 * Ctor. Creates the CommandContext.
	 * 
	 * @param config
	 *            Configuration of the currently running zoneserver.
	 * @param zoneserver
	 *            Handle to the zoneserver to retrieve various information about
	 *            it.
	 * 
	 */
	public CommandContext(BestiaConfiguration config, Zoneserver zoneserver) {

		if (config == null) {
			throw new IllegalArgumentException("config can not be null.");
		}

		if (zoneserver == null) {
			throw new IllegalArgumentException("zoneserver can not be null.");
		}

		this.configuration = config;
		this.server = zoneserver;
		this.serviceLocator = ServiceLocator.getInstance();
	}

	/**
	 * Returns the server configuration.
	 * 
	 * @return Configuration of the server.
	 */
	public BestiaConfiguration getConfiguration() {
		return configuration;
	}

	/**
	 * Returns the zoneserver instance.
	 * 
	 * @return Zoneserver instance.
	 */
	public Zoneserver getServer() {
		return server;
	}

	/**
	 * Service locator for creating services and DAO.
	 * 
	 * @return
	 */
	public ServiceLocator getServiceLocator() {
		return serviceLocator;
	}

}
