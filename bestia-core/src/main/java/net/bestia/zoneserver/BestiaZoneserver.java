package net.bestia.zoneserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import net.bestia.core.game.zone.Zone;
import net.bestia.interserver.InterserverConnectionFactory;
import net.bestia.interserver.InterserverMessageHandler;
import net.bestia.interserver.InterserverSubscriber;
import net.bestia.messages.Message;
import net.bestia.util.BestiaConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BestiaZoneserver {

	private final static Logger log = LogManager.getLogger(BestiaZoneserver.class);

	public final static String VERSION;
	static {
		String version = "NOT-ASSIGNED-ERROR";
		// Find the version number from the maven build script.
		try {
			File versionFile = new File(BestiaZoneserver.class.getClassLoader().getResource("buildnumber.txt").toURI());
			BufferedReader br = new BufferedReader(new FileReader(versionFile));
			version = br.readLine();
			br.close();
		} catch (IOException | URISyntaxException e) {
			log.error("Error while reading version file.", e);
		}
		VERSION = version;
	}

	private class InterserverHandler implements InterserverMessageHandler {

		@Override
		public void onMessage(Message msg) {
			log.info("Received: {}", msg.toString());
		}

		@Override
		public void connectionLost() {
			// TODO Auto-generated method stub

		}

	}

	private final String name;
	private final BestiaConfiguration config;
	private final AtomicBoolean isRunning = new AtomicBoolean(false);
	private final InterserverHandler interserverHandler = new InterserverHandler();
	private final InterserverSubscriber interserverSubscriber;

	/**
	 * List of zones for which this server is responsible.
	 */
	private final Map<String, Zone> zones = new HashMap<>();

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
	public BestiaZoneserver() {

		// #### Config: Setup the config file.
		this.config = new BestiaConfiguration();
		try {
			config.load();
		} catch (IOException e) {
			log.fatal("Can not read from config file. Stopping.", e);
			System.exit(1);
		}

		this.name = config.getProperty("zone.name");

		InterserverConnectionFactory interservConFactory = new InterserverConnectionFactory(1);
		interserverSubscriber = interservConFactory.getSubscriber(interserverHandler, "tcp://localhost:9800");
	}

	/**
	 * Ctor. For providing an external executor service. This is mostly for internal use for unit testing purposes to
	 * run the commands in the same thread as the unit tests. Should not be needed to be used in production.
	 * 
	 * @param serviceFactory
	 * @param connection
	 * @param configFile
	 * @param execService
	 */
	/*
	 * public BestiaZoneserver(ServiceFactory serviceFactory, BestiaConnectionInterface connection, String configFile,
	 * ExecutorService execService) {
	 * 
	 * // #### Config: Setup the config file. initilizeConfig(configFile);
	 * 
	 * // #### Variable setup. worker = execService;
	 * 
	 * this.messenger = new Messenger(connection, worker); this.name = config.getProperty("name"); this.commandContext =
	 * new CommandContext.Builder ().setMessenger(messenger).setServiceFactory(serviceFactory) .setZones(zones).build();
	 * this.cmdFactory = new CommandFactory(); }
	 */

	/**
	 * Starts the server. Initializes all the messaging pipeline, database connections, cache and scripts.
	 */
	public void start() {

		if (isRunning.getAndSet(true)) {
			log.warn("Zoneserver is already running.");
			return;
		}

		// Initializing all messaging components.
		log.info("Zoneserver is starting...");

		// Create ScriptInitWorker: Reading and compiling all the scripts.
		// log.info("Initializing: scripts...");
		// ScriptInitWorker siworker = new ScriptInitWorker();
		// siworker.run();

		// Create ActorInitWorker: Spawning and initializing all Actors.
		// ZoneInitLoader zoneLoader = new ZoneInitLoader(config, zones);
		// zoneLoader.init();

		// Create ActorInitWorker: Spawning and initializing all Actors.
		log.info("Initializing: actors...");

		log.info("Initializing: message queue...");

		log.info("Registering with Interserver...");
		interserverSubscriber.connect();
		// Subscribe to zone broadcast messages.
		//interserverSubscriber.subscribe("zone/all");
		// Subscribe to messages explicity for this zone.
		//interserverSubscriber.subscribe("zone/" + name);
		
		// TODO Temporär. Subscribe zu account message.
		// Das muss dann dynamisch geschehen mit allen eingeloggten accounts.
		//interserverSubscriber.subscribe("zone/account/1");

		log.info("Bestia Behemoth Zone [{}] has started.", name);
	}


	/**
	 * Ceases all server operation and persists all pending data.
	 */
	public void stop() {
		// TODO das hier noch fertig machen und alles runterfahren.
		stop(false);
	}

	/**
	 * Ceases all server operation and persists all pending data.
	 * 
	 * @param waitLong
	 *            If TRUE the server will wait for a far longer timeout until it terminates all pending commands. Nice
	 *            for debugging.
	 */
	public void stop(boolean waitLong) {

		log.info("Bestia Behemoth Server is stopping...");
		log.info("Unsubscribe from Interserver...");

		// Shut down all the msg queues.
		log.info("Shutting down: command and messaging system...");

		log.info("Shutting down: actor subsystem...");

		// Wait for all threads to cease operation.

		log.info("Zone: [{}] went down.", config.getProperty("name"));
	}

	/**
	 * Let the Server handle one message. It wraps the message asynchronously in a command with a given context so the
	 * command in turn can be executed asynchronously aswell. The resulting messages are send out over the connections
	 * to the clients.
	 * 
	 * @param message
	 * @return
	 */
	public void handleMessage(final Message message) {

	}

	/**
	 * Returns the name of this zoneserver instance.
	 * 
	 * @return Name of this zoneserver.
	 */
	public String getName() {
		return name;
	}

}
