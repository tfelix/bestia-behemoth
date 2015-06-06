package net.bestia.zoneserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
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
import net.bestia.messages.PongMessage;
import net.bestia.messages.RequestLoginMessage;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandFactory;
import net.bestia.zoneserver.game.zone.Zone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


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

	private final static Logger log = LogManager
			.getLogger(Zoneserver.class);

	public final static String VERSION;
	static {
		String version = "NOT-ASSIGNED-ERROR";
		// Find the version number from the maven build script.
		try {
			File versionFile = new File(Zoneserver.class.getClassLoader()
					.getResource("buildnumber.txt").toURI());
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
			log.trace("Zoneserver received: {}", msg.toString());

			Command cmd = commandFactory.getCommand(msg);

			switch (msg.getMessageId()) {

			case RequestLoginMessage.MESSAGE_ID:
				// TODO Usually we would like a message/command dispatch
				// service. Create a command execute it and then
				// send back the server answer.
				RequestLoginMessage message = (RequestLoginMessage) msg;
				PongMessage pong = new PongMessage(msg);
				try {
					interserverPublisher.publish(pong);
				} catch (IOException e) {
					// TODO hier noch alles machen.
				}
				break;
			}
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
	private final InterserverPublisher interserverPublisher;

	private final CommandFactory commandFactory;
	private final ExecutorService commandExecutor;

	/**
	 * List of zones for which this server is responsible.
	 */
	private final Map<String, Zone> zones = new HashMap<>();

	private final Set<String> responsibleZones;

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
	public Zoneserver() {

		// Setup the config file.
		this.config = new BestiaConfiguration();
		try {
			config.load();
		} catch (IOException e) {
			log.fatal("Can not read from config file. Stopping.", e);
			System.exit(1);
		}

		this.name = config.getProperty("zone.name");

		this.commandFactory = new CommandFactory();
		this.commandExecutor = Executors.newFixedThreadPool(1);

		final String interUrl = config.getProperty("inter.domain");
		final int listenPort = config.getIntProperty("inter.listenPort");
		final int subscribePort = config.getIntProperty("inter.subscribePort");

		InterserverConnectionFactory conFactory = new InterserverConnectionFactory(
				1, interUrl, listenPort, subscribePort);

		interserverSubscriber = conFactory.getSubscriber(interserverHandler);
		interserverPublisher = conFactory.getPublisher();
		
		// Generate the list of zones for this server.
		String[] zoneStrings = config.getProperty("zone.zones").split(",");
		Set<String> zones = new HashSet<String>();
		zones.addAll(Arrays.asList(zoneStrings));
		
		this.responsibleZones = Collections.unmodifiableSet(zones);
	}

	/**
	 * Starts the server. Initializes all the messaging pipeline, database
	 * connections, cache and scripts.
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
		interserverPublisher.connect();

		// Subscribe to zone broadcast messages.
		interserverSubscriber.subscribe("zone/all");
		// Subscribe to messages explicity for this zone.
		interserverSubscriber.subscribe("zone/" + name);

		// TODO Temporär. Subscribe zu account message.
		// Das muss dann dynamisch geschehen mit allen eingeloggten accounts.
		// interserverSubscriber.subscribe("zone/account/1");

		log.info("Bestia Behemoth Zone [{}] has started.", name);
	}

	/**
	 * Ceases all server operation and persists all pending data.
	 */
	public void stop() {

		log.info("Bestia Behemoth Server is stopping...");
		log.info("Unsubscribe from Interserver...");

		// Shut down all the msg queues.
		log.info("Shutting down: command and messaging system...");

		log.info("Shutting down: entity subsystem...");

		// Wait for all threads to cease operation.

		log.info("Zone: [{}] went down.", config.getProperty("name"));
	}

	/**
	 * Sends the message back to the interserver. One must make sure the message
	 * path is now correct since this basically tells the interserver how to
	 * process this message.
	 * 
	 * @param message
	 */
	public void sendMessage(final Message message) {
		try {
			interserverPublisher.publish(message);
		} catch (IOException e) {
			log.trace("Error: Could not deliver message: {}",
					message.toString());
		}
	}

	/**
	 * This will schedule a command for execution. Since the commands receive a
	 * {@link CommandContext} which in turn holds a reference to a
	 * {@link Zoneserver} commands can trigger new commands on their own.
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
	 * Returns a set of zone names (map_db_name of a map) the server is
	 * responsible for.
	 * 
	 * @return Set of names for which this server is responsible.
	 */
	public Set<String> getResponsibleZones() {
		return responsibleZones;
	}

	public static void main(String[] args) {
		Zoneserver zone = new Zoneserver();
		zone.start();
	}

}
