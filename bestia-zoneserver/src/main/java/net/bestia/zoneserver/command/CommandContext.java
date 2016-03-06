package net.bestia.zoneserver.command;

import net.bestia.model.ServiceLocator;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.messaging.AccountRegistry;
import net.bestia.zoneserver.messaging.routing.MessageRouter;
import net.bestia.zoneserver.script.ScriptManager;

/**
 * Facade class to cover the needed information context for a {@link Command}
 * class. It contains references to database access and messaging API. The
 * context itself is immutable because multi-thread access will happen. All the
 * member of this class must therefore be thread safe as well.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class CommandContext {

	public static class CommandContextBuilder {

		private BestiaConfiguration configuration;
		private Zoneserver server;
		private ServiceLocator serviceLocator;
		private ScriptManager scriptManager;
		private MessageRouter messageRouter;
		private AccountRegistry accountRegistry;

		public CommandContextBuilder setConfiguration(BestiaConfiguration configuration) {
			this.configuration = configuration;
			return this;
		}

		public CommandContextBuilder setServer(Zoneserver server) {
			this.server = server;
			return this;
		}

		public CommandContextBuilder setServiceLocator(ServiceLocator serviceLocator) {
			this.serviceLocator = serviceLocator;
			return this;
		}

		public CommandContextBuilder setScriptManager(ScriptManager scriptManager) {
			this.scriptManager = scriptManager;
			return this;
		}

		public CommandContext build() {
			final CommandContext ctx = new CommandContext(this);
			return ctx;
		}

		public CommandContextBuilder setMessageRouter(MessageRouter messageRouter) {
			this.messageRouter = messageRouter;
			return this;
		}

		public CommandContextBuilder setAccountRegistry(AccountRegistry accountRegistry) {
			this.accountRegistry = accountRegistry;
			return this;
		}
	}

	private final BestiaConfiguration configuration;
	private final Zoneserver server;
	private final ServiceLocator serviceLocator;
	private final ScriptManager scriptManager;
	private final MessageRouter messageRouter;
	private final AccountRegistry accountRegistry;

	/**
	 * Ctor. Creates the CommandContext.
	 * 
	 * 
	 */
	private CommandContext(CommandContextBuilder builder) {
		
		if(builder.configuration == null) {
			throw new IllegalArgumentException("Configuration can not be null.");
		}
		if(builder.server == null) {
			throw new IllegalArgumentException("Server can not be null.");
		}
		if(builder.serviceLocator == null) {
			throw new IllegalArgumentException("ServiceLocator can not be null.");
		}
		if(builder.scriptManager == null) {
			throw new IllegalArgumentException("ScriptManager can not be null.");
		}
		if(builder.messageRouter == null) {
			throw new IllegalArgumentException("MessageRouter can not be null.");
		}
		if(builder.accountRegistry == null) {
			throw new IllegalArgumentException("AccountRegistry can not be null.");
		}

		this.configuration = builder.configuration;
		this.server = builder.server;
		this.serviceLocator = builder.serviceLocator;
		this.scriptManager = builder.scriptManager;
		this.messageRouter = builder.messageRouter;
		this.accountRegistry = builder.accountRegistry;
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
	 * Returns the {@link MessageRouter} of the bestia zone server.
	 * 
	 * @return MessageRouter of the server.
	 */
	public MessageRouter getMessageRouter() {
		return messageRouter;
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
	
	/**
	 * Returns the account registry. TODO hier überlegen ob das nicht besser an die passenden stellen soll.
	 * @return
	 */
	public AccountRegistry getAccountRegistry() {
		return accountRegistry;
	}

}
