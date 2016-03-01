package net.bestia.zoneserver;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.interserver.InterserverConnectionFactory;
import net.bestia.interserver.InterserverMessageHandler;
import net.bestia.interserver.InterserverPublisher;
import net.bestia.interserver.InterserverSubscriber;
import net.bestia.messages.Message;
import net.bestia.model.I18n;
import net.bestia.model.ServiceLocator;
import net.bestia.model.dao.I18nDAO;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.command.CommandContext.CommandContextBuilder;
import net.bestia.zoneserver.command.CommandFactory;
import net.bestia.zoneserver.command.server.ServerCommandFactory;
import net.bestia.zoneserver.loader.ScriptLoader;
import net.bestia.zoneserver.loader.ZoneLoader;
import net.bestia.zoneserver.messaging.AccountRegistry;
import net.bestia.zoneserver.messaging.MessageLoop;
import net.bestia.zoneserver.messaging.preprocess.MessagePreprocessor;
import net.bestia.zoneserver.messaging.preprocess.MessagePreprocessorController;
import net.bestia.zoneserver.messaging.routing.MessageRouter;
import net.bestia.zoneserver.script.ScriptManager;
import net.bestia.zoneserver.zone.Zone;

/**
 * This is the central game server instance. Upon start it will read all
 * designated maps parse them, instance all needed entities and scripts. It will
 * then populate the map with the spawned entities and with data from the
 * database and start simulating the game environment.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Zoneserver {

	private final static Logger log = LogManager.getLogger(Zoneserver.class);

	private final String name;
	private final BestiaConfiguration config;
	private final AtomicBoolean isRunning = new AtomicBoolean(false);

	private final InterserverMessageHandler interserverHandler = new InterserverMessageHandler() {

		@Override
		public void onMessage(Message msg) {
			log.trace("Zoneserver {} received: {}", name, msg.toString());
			messageLoop.handleMessage(msg);
		}
	};

	private final InterserverSubscriber interserverSubscriber;
	private final InterserverPublisher interserverPublisher;

	private final MessageLoop messageLoop;

	private final CommandContext commandContext;

	/**
	 * List of zones for which this server is responsible.
	 */
	private final Map<String, Zone> zones = new HashMap<>();
	private final Set<String> responsibleZones;

	private final ScriptManager scriptManager = new ScriptManager();

	private final AccountRegistry accountRegistry;

	/**
	 * Ctor. The server needs a connection to its clients so it can use the
	 * messaging API to communicate with them.
	 * 
	 * @param serviceFactory
	 *            Service factory to getting the game beans from a datasource.
	 * @param connection
	 *            Manager to hold the connections to the clients.
	 * @param configFile
	 *            File with config settings for the bestia server.
	 */
	public Zoneserver(BestiaConfiguration config) {

		if (config == null || !config.isLoaded()) {
			throw new IllegalArgumentException("Config can not be null or unloaded.");
		}

		// Setup the config file.
		this.config = config;

		// Name of the zoneserver.
		this.name = config.getProperty("zone.name");

		final String interUrl = config.getProperty("inter.domain");
		// We receive where the interserver publishes and vice versa, thats why
		// its in the wrong order.
		final int subscribePort = config.getIntProperty("inter.listenPort");
		final int listenPort = config.getIntProperty("inter.publishPort");

		final InterserverConnectionFactory conFactory = new InterserverConnectionFactory(1,
				interUrl,
				listenPort,
				subscribePort);

		this.interserverSubscriber = conFactory.getSubscriber(interserverHandler);
		this.interserverPublisher = conFactory.getPublisher();

		this.accountRegistry = new AccountRegistry(interserverSubscriber);

		// Create a command context.
		final CommandContextBuilder ctxBuilder = new CommandContextBuilder();
		ctxBuilder.setConfiguration(config)
				.setServer(this)
				.setScriptManager(scriptManager)
				.setServiceLocator(ServiceLocator.getInstance())
				.setMessageRouter(new MessageRouter())
				.setAccountRegistry(accountRegistry);
		this.commandContext = ctxBuilder.build();

		final MessagePreprocessor preprocessor = new MessagePreprocessorController(commandContext);
		final CommandFactory serverCommandFactory = new ServerCommandFactory(commandContext);
		this.messageLoop = new MessageLoop(preprocessor, serverCommandFactory, commandContext.getMessageRouter());

		// Generate the list of zones for this server.
		final String[] zoneStrings = config.getProperty("zone.zones").split(",");
		final Set<String> zones = new HashSet<String>();
		zones.addAll(Arrays.asList(zoneStrings));
		this.responsibleZones = Collections.unmodifiableSet(zones);

		// Prepare the (static) translator.
		I18n.setDao(commandContext.getServiceLocator().getBean(I18nDAO.class));
	}

	/**
	 * Starts the server. Initializes all the messaging pipeline, database
	 * connections, cache and scripts.
	 * 
	 * @return {@code TRUE} if started. {@code FALSE} otherwise.
	 */
	public boolean start() {

		if (isRunning.getAndSet(true)) {
			log.warn("Zoneserver is already running.");
			return false;
		}

		// Initializing all messaging components.
		log.info(config.getVersion());
		log.info("Zoneserver is starting...");

		// Create ScriptCacheLoader: Reading and compiling all the scripts.
		final ScriptLoader scriptLoader = new ScriptLoader(config, commandContext, scriptManager);
		try {
			scriptLoader.init();
		} catch (IOException ex) {
			stop();
			return false;
		}

		// Create ActorInitWorker: Spawning and initializing all Actors.
		log.info("Initializing: maps...");
		ZoneLoader zoneLoader = new ZoneLoader(commandContext, zones);
		try {
			zoneLoader.init();
		} catch (IOException ex) {
			log.error("There was an error while loading the maps.", ex);
			stop();
			return false;
		}

		// Create ActorInitWorker: Spawning and initializing all Actors.
		log.info("Initializing: zones...");
		try {
			zones.values().forEach((x) -> x.start());
		} catch (Exception ex) {
			// Whatever could go wrong...
			log.fatal("Could not start zones.", ex);
			stop();
			return false;
		}

		log.info("Registering with Interserver...");
		try {
			interserverSubscriber.connect();
			interserverPublisher.connect();
		} catch (IOException ex) {
			log.error("Can not start zoneserver.", ex);
			stop();
			return false;
		}

		// Subscribe to zone broadcast messages.
		interserverSubscriber.subscribe("zone/all");

		// Subscribe to messages directed for this zone.
		interserverSubscriber.subscribe("zone/" + name);

		log.info("Bestia Behemoth Zone [{}] has started.", name);
		return true;
	}

	/**
	 * Ceases all server operation and persists all pending data.
	 */
	public void stop() {
		log.info("Bestia Behemoth Server is stopping...");

		log.info("Unsubscribe from Interserver...");
		interserverSubscriber.disconnect();
		interserverPublisher.disconnect();

		// Shut down all the msg queues.
		log.info("Shutting down: command and messaging system...");
		messageLoop.shutdown();

		log.info("Shutting down: zones entity subsystem...");
		zones.values().forEach((x) -> {
			x.stop();
		});

		// Wait for all threads to cease operation.
		log.info("Zone: [{}] went down.", name);
	}

	/**
	 * Returns the name of this {@link Zoneserver} instance.
	 * 
	 * @return Name of this zoneserver.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns a set of zone names (map_db_name of a map) the server is
	 * responsible for.
	 * 
	 * @return Set of names for which this server is responsible.
	 */
	public Set<String> getResponsibleZones() {
		return responsibleZones;
	}

	/**
	 * Returns the zone with the given name.
	 * 
	 * @param name
	 *            Name of the zone to return.
	 * @return Zone or null if this zone does not exist.
	 */
	public Zone getZone(String name) {
		return zones.get(name);
	}

	/**
	 * Subscribes to a topic on the subscriber from the interserver.
	 * 
	 * @param topic
	 *            Topic to subscribe to.
	 */

	public void subscribe(String topic) {
		interserverSubscriber.subscribe(topic);
	}

	/**
	 * Unsubscribes from a topic on the subscriber from the interserver.
	 * 
	 * @param topic
	 *            Topic to unsubscribe from.
	 */

	public void unsubscribe(String topic) {
		interserverSubscriber.unsubscribe(topic);
	}

	/**
	 * Entry point. Starts the server.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		// Create cmd line arguments.
		final Options options = new Options();

		options.addOption(Option.builder("cf")
				.longOpt("configFile")
				.hasArg()
				.desc("path to an external config file.")
				.build());

		options.addOption(Option.builder("cl")
				.longOpt("clean")
				.hasArg()
				.desc("Zones are started without loading persisted entities.")
				.build());

		CommandLineParser parser = new DefaultParser();
		final BestiaConfiguration config = new BestiaConfiguration();
		try {
			final CommandLine cmd = parser.parse(options, args);

			if (cmd.hasOption("cf")) {
				final String configFile = cmd.getOptionValue("cf");
				log.info("Use config file: " + configFile);
				config.load(new File(configFile));
			} else {
				config.load();
			}

			if (cmd.hasOption("cl")) {
				config.setValue("zone.cleanLoad", 1);
			}
		} catch (ParseException e) {
			log.fatal("Could not parse the commandline. Exit.");
			System.exit(1);
		} catch (IOException e) {
			log.fatal("Could not read the config file.");
			System.exit(1);
		}

		final Zoneserver zone = new Zoneserver(config);
		if (!zone.start()) {
			System.exit(1);
		}
	}

	/**
	 * Process/Sends the message to the interserver.
	 * <p>
	 * NOTE: I know this is a bit unclean. Using one single method for internal
	 * rerouting and external messaging would be cleaner. Maybe the router can
	 * be improved to archive this. Nether the less access to the router is
	 * always needed in order to dynamically subscribe new message filter.
	 * </p>
	 */
	public void sendMessage(Message message) {
		log.trace("Sending: {}", message.toString());
		try {
			interserverPublisher.publish(message);
		} catch (IOException e) {
			log.trace("Error: Could not deliver message: {}", message.toString(), e);
		}
	}
}
