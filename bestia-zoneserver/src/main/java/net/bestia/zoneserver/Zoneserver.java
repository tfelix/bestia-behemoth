package net.bestia.zoneserver;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import net.bestia.interserver.InterserverConnectionFactory;
import net.bestia.interserver.InterserverMessageHandler;
import net.bestia.interserver.InterserverPublisher;
import net.bestia.interserver.InterserverSubscriber;
import net.bestia.messages.Message;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.command.CommandFactory;
import net.bestia.zoneserver.ecs.InputController;
import net.bestia.zoneserver.ecs.InputController.InputControllerCallback;
import net.bestia.zoneserver.worker.ZoneInitLoader;
import net.bestia.zoneserver.zone.Zone;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is the central game server instance. Upon start it will read all designated maps parse them, instance all needed
 * entities and scripts. It will then populate the map with the spawned entities and with data from the database and
 * start simulating the game environment.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Zoneserver {

	private final static Logger log = LogManager.getLogger(Zoneserver.class);
	
	private class InputControllerCallbackImpl implements InputControllerCallback {

		@Override
		public void removedBestia(long accId, int bestiaId) {
			// no op.
		}

		@Override
		public void removedAccount(long accountId) {
			unsubscribe("zone/account/" + accountId);
			log.trace("Unregistered account {} from zone {}.", accountId, name);	
		}

		/**
		 * New account added to this input controller. Register it with this server.
		 */
		@Override
		public void addedAccount(long accountId) {
			subscribe("zone/account/" + accountId);
			log.trace("Registered account {} on zone {}.", accountId, name);	
		}

		@Override
		public void addedBestia(long accId, int bestiaId) {
			// no op.
		}
		
	}

	private class InterserverHandler implements InterserverMessageHandler {

		@Override
		public void onMessage(Message msg) {
			log.trace("Zoneserver {} received: {}", name, msg.toString());

			// Create command out of the message and deliver it to the executor.
			final Command cmd = commandFactory.getCommand(msg);
			commandExecutor.execute(cmd);
		}
	}

	private final String name;
	private final BestiaConfiguration config;
	private final AtomicBoolean isRunning = new AtomicBoolean(false);

	private final InterserverHandler interserverHandler = new InterserverHandler();
	private final InterserverSubscriber interserverSubscriber;
	private final InterserverPublisher interserverPublisher;

	private final CommandFactory commandFactory;
	private final ExecutorService commandExecutor;
	private final CommandContext commandContext;

	/**
	 * List of zones for which this server is responsible.
	 */
	private final Map<String, Zone> zones = new HashMap<>();
	private final Set<String> responsibleZones;

	private final InputController ecsInputController = new InputController();

	/**
	 * Ctor. The server needs a connection to its clients so it can use the messaging API to communicate with them.
	 * 
	 * @param serviceFactory
	 *            Service factory to getting the game beans from a datasource.
	 * @param connection
	 *            Manager to hold the connections to the clients.
	 * @param configFile
	 *            File with config settings for the bestia server.
	 */
	public Zoneserver(BestiaConfiguration config) {
		
		if(config == null || !config.isLoaded()) {
			throw new IllegalArgumentException("Config can not be null or unloaded.");
		}

		// Setup the config file.
		this.config = config;

		this.name = config.getProperty("zone.name");

		// Create a command context.
		final CommandContext.Builder cmdCtxBuilder = new CommandContext.Builder();

		cmdCtxBuilder.setConfiguration(config).setZones(zones).setZoneserver(this);
		commandContext = cmdCtxBuilder.build();

		this.commandFactory = new CommandFactory(commandContext);
		this.commandExecutor = Executors.newFixedThreadPool(1);

		final String interUrl = config.getProperty("inter.domain");
		// We receive where the interserver publishes and vice versa.
		final int subscribePort = config.getIntProperty("inter.listenPort");
		final int listenPort = config.getIntProperty("inter.publishPort");

		InterserverConnectionFactory conFactory = new InterserverConnectionFactory(1, interUrl, listenPort,
				subscribePort);

		interserverSubscriber = conFactory.getSubscriber(interserverHandler);
		interserverPublisher = conFactory.getPublisher();

		// Generate the list of zones for this server.
		String[] zoneStrings = config.getProperty("zone.zones").split(",");
		Set<String> zones = new HashSet<String>();
		zones.addAll(Arrays.asList(zoneStrings));

		this.responsibleZones = Collections.unmodifiableSet(zones);
		
		this.ecsInputController.addCallback(new InputControllerCallbackImpl());
	}

	/**
	 * Starts the server. Initializes all the messaging pipeline, database connections, cache and scripts.
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

		// Create ScriptInitWorker: Reading and compiling all the scripts.
		// log.info("Initializing: scripts...");
		// ScriptInitWorker siworker = new ScriptInitWorker();
		// siworker.run();

		// Create ActorInitWorker: Spawning and initializing all Actors.
		log.info("Initializing: maps...");
		ZoneInitLoader zoneLoader = new ZoneInitLoader(commandContext, zones);
		try {
			zoneLoader.init();
		} catch (IOException ex) {
			log.error("There was an error while loading the maps.", ex);
			stop();
			return false;
		}

		// Create ActorInitWorker: Spawning and initializing all Actors.
		log.info("Initializing: actors...");
		zones.values().forEach((x) -> x.start());

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

		// Subscribe to messages explicity for this zone.
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
		commandExecutor.shutdown();

		log.info("Shutting down: zones entity subsystem...");
		zones.values().forEach((x) -> {
			x.stop();
		});

		// Wait for all threads to cease operation.
		log.info("Zone: [{}] went down.", name);
	}

	/**
	 * Sends the message back to the interserver. One must make sure the message path is now correct since this
	 * basically tells the interserver how to process this message.
	 * 
	 * @param message
	 */
	public void sendMessage(final Message message) {
		try {
			interserverPublisher.publish(message);
		} catch (IOException e) {
			log.trace("Error: Could not deliver message: {}", message.toString(), e);
		}
	}

	/**
	 * This will schedule a command for execution. Since the commands receive a {@link CommandContext} which in turn
	 * holds a reference to a {@link Zoneserver} commands can trigger new commands on their own.
	 * 
	 * @param cmd
	 *            {@link Command} implementation to be executed.
	 */
	public void executeCommand(Command cmd) {
		commandExecutor.execute(cmd);
	}

	/**
	 * Returns the name of this zoneserver instance.
	 * 
	 * @return Name of this zoneserver.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns a set of zone names (map_db_name of a map) the server is responsible for.
	 * 
	 * @return Set of names for which this server is responsible.
	 */
	public Set<String> getResponsibleZones() {
		return responsibleZones;
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
	 * UNsubscribes from a topic on the subscriber from the interserver.
	 * 
	 * @param topic
	 *            Topic to unsubscribe from.
	 */
	public void unsubscribe(String topic) {
		interserverSubscriber.unsubscribe(topic);
	}

	
	/**
	 * Returns a {@link InputController}. It will be used by the ECS to fetch the player input async aswell as the
	 * commands to pipe the player input for the different bestias to the ECS.
	 * 
	 * @return
	 */
	public InputController getInputController() {
		return ecsInputController;
	}

	/**
	 * Entry point. Starts the server.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		
		// Create cmd line arguments.
		Options options = new Options();
		
		Option configFileProp   = OptionBuilder.withArgName( "cf" )
				.withLongOpt("configFile")
                .hasArg()
                .withDescription(  "use an external config file." )
                .create( "config" );
		
		options.addOption(configFileProp);
		
		// TODO das CMD parsing hier noch in eine extra datei packen.
		CommandLineParser parser = new DefaultParser();
		final BestiaConfiguration config = new BestiaConfiguration();
		try {
			CommandLine cmd = parser.parse(options, args);
			
			if(cmd.hasOption("config")) {
				final String configFile = cmd.getOptionValue("config");
				log.info("Use config file: " + configFile);
				config.load(new File(configFile));
			} else {
				config.load();
			}	
		} catch (ParseException e) {
			log.fatal("Could not parse the commandline. Exit.");
			System.exit(1);
		} catch(IOException e) {
			log.fatal("Could not read the config file.");
			System.exit(1);
		}
		
		final Zoneserver zone = new Zoneserver(config);
		if (!zone.start()) {
			System.exit(1);
		}
	}
}
