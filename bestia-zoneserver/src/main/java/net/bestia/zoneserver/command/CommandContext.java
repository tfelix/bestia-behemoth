package net.bestia.zoneserver.command;

import net.bestia.model.ServiceLocator;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.Zoneserver;
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
	}

	private final BestiaConfiguration configuration;
	private final Zoneserver server;
	private final ServiceLocator serviceLocator;
	private final ScriptManager scriptManager;
	private final MessageRouter messageRouter;

	/**
	 * Ctor. Creates the CommandContext.
	 * 
	 * 
	 */
	private CommandContext(CommandContextBuilder builder) {

		this.configuration = builder.configuration;
		this.server = builder.server;
		this.serviceLocator = builder.serviceLocator;
		this.scriptManager = builder.scriptManager;
		this.messageRouter = builder.messageRouter;
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

}
