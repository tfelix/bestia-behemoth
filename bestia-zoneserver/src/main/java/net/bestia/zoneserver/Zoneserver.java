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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import net.bestia.interserver.InterserverConnectionFactory;
import net.bestia.interserver.InterserverMessageHandler;
import net.bestia.interserver.InterserverPublisher;
import net.bestia.interserver.InterserverSubscriber;
import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.command.CommandFactory;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;
import net.bestia.zoneserver.game.worker.ZoneInitLoader;
import net.bestia.zoneserver.game.zone.Zone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mysql.jdbc.NotImplemented;

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

	public final static String VERSION;
	static {
		String version = "NOT-ASSIGNED-ERROR";
		// Find the version number from the maven build script.
		try {
			File versionFile = new File(Zoneserver.class.getClassLoader().getResource("buildnumber.txt").toURI());
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

	private final Map<Long, Map<Integer, PlayerBestia>> activeBestias = new ConcurrentHashMap<>();
	private final Map<Integer, Zone> activeBestiasZoneCache = new ConcurrentHashMap<>();

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
	}

	/**
	 * Registers an account with this server. It will register all associated player bestias with the entity system.
	 * 
	 * @param accId
	 */
	public void registerAccount(long accId) {
		final PlayerBestiaDAO pbDAO = commandContext.getServiceLocator().getBean(PlayerBestiaDAO.class);
		final Account account = commandContext.getServiceLocator().getBean(AccountDAO.class).find(accId);

		boolean hasAtLeastOneEntity = false;
		Map<Integer, PlayerBestia> activeBestias = new ConcurrentHashMap<>();

		// Check if this player has bestias or his master on this zone.
		final PlayerBestia master = account.getMaster();
		hasAtLeastOneEntity = addToZone(accId, master);
		
		Set<PlayerBestia> bestias = pbDAO.findPlayerBestiasForAccount(accId);

		// Create bestia entity.
		for (PlayerBestia playerBestia : bestias) {
			if (addToZone(accId, playerBestia)) {
				hasAtLeastOneEntity = true;
			}
		}

		// We have bestias from this account on this zone.
		// Register this zone now as responsible for handling messages regarding this account.
		if (hasAtLeastOneEntity) {
			subscribe("zone/account/" + accId);
			log.debug("Registered account {} with {} bestias on zone.", accId, activeBestias.size());
		}
	}

	private boolean addToZone(long accId, PlayerBestia pb) {
		if (!isOnZone(pb)) {
			return false;
		}

		Zone z = getResponsibleZone(pb);
		z.addPlayerBestia(new PlayerBestiaManager(pb, this));

		if (!activeBestias.containsKey(accId)) {
			activeBestias.put(accId, new ConcurrentHashMap<>());
		}
		activeBestias.get(accId).put(pb.getId(), pb);
		// Save zone in the cache for fast resolution of messages.
		activeBestiasZoneCache.put(pb.getId(), z);
		return true;
	}

	private boolean isOnZone(PlayerBestia pb) {
		return responsibleZones.contains(pb.getCurrentPosition().getMapDbName());
	}

	private Zone getResponsibleZone(PlayerBestia pb) {
		final String mapDbName = pb.getCurrentPosition().getMapDbName();
		return zones.get(mapDbName);
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
		log.info("Zoneserver is starting...");

		// Create ScriptInitWorker: Reading and compiling all the scripts.
		// log.info("Initializing: scripts...");
		// ScriptInitWorker siworker = new ScriptInitWorker();
		// siworker.run();

		// Create ActorInitWorker: Spawning and initializing all Actors.
		log.info("Initializing: maps...");
		ZoneInitLoader zoneLoader = new ZoneInitLoader(getResponsibleZones(), config, zones);
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

	public void processPlayerInput(Message msg) {
		
		if(msg.getPlayerBestiaId() == 0) {
			log.warn("PlayerInput: No player bestia id given. %s", msg);
			return;
		}
		
		// Sanitycheck: Is player really owner of this bestia?
		if(!activeBestias.get(msg.getAccountId()).containsKey(msg.getPlayerBestiaId())) {
			log.warn("HACK: Account {} does not own bestia with id {}!", msg.getAccountId(), msg.getPlayerBestiaId());
			return;
		}
		
		Zone z = activeBestiasZoneCache.get(msg.getPlayerBestiaId());
		z.processPlayerInput(msg);
	}

	/**
	 * Entry point. Starts the server.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Zoneserver zone = new Zoneserver();
		if (!zone.start()) {
			System.exit(1);
		}
	}

	/**
	 * TODO Noch implementierne.
	 * @return
	 */
	public ECSInputControler getInputController() {
		return null;
	}

}
