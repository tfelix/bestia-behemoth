package net.bestia.zoneserver.command;

import net.bestia.model.ServiceLocator;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.script.ScriptManager;

/**
 * Facade class to cover the needed information context for a {@link Command} class. It contains references to database
 * access and messaging API. The context itself is immutable because multi-thread access will happen. All the member of
 * this class must therefore be thread safe as well.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class CommandContext {

	private final BestiaConfiguration configuration;
	private final Zoneserver server;
	private final ServiceLocator serviceLocator;
	private final ScriptManager scriptManager;

	/**
	 * Ctor. Creates the CommandContext.
	 * 
	 * @param config
	 *            Configuration of the currently running zoneserver.
	 * @param zoneserver
	 *            Handle to the zoneserver to retrieve various information about it.
	 * @param scriptManager
	 *            Handle to the central instance of the script manager to call scripts.
	 * 
	 */
	public CommandContext(BestiaConfiguration config, Zoneserver zoneserver, ScriptManager scriptManager) {

		if (config == null) {
			throw new IllegalArgumentException("config can not be null.");
		}

		if (zoneserver == null) {
			throw new IllegalArgumentException("zoneserver can not be null.");
		}
		
		if(scriptManager == null) {
			throw new IllegalArgumentException("scriptManager can not be null.");
		}

		this.configuration = config;
		this.server = zoneserver;
		this.serviceLocator = ServiceLocator.getInstance();
		this.scriptManager = scriptManager;
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

	/**
	 * The script manager to fire off bestia scripts.
	 * 
	 * @return
	 */
	public ScriptManager getScriptManager() {
		return scriptManager;
	}

}
