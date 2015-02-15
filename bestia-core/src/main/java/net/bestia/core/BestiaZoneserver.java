package net.bestia.core;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.bestia.core.command.Command;
import net.bestia.core.command.CommandFactory;
import net.bestia.core.connection.BestiaConnectionInterface;
import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.game.worker.ScriptInitWorker;
import net.bestia.core.message.Message;
import net.bestia.core.net.Messenger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BestiaZoneserver {

	private final static Logger log = LogManager
			.getLogger(BestiaZoneserver.class);

	private final CommandFactory cmdFactory;
	private final Messenger messenger;
	private Properties config = new Properties();
	private boolean isRunning = false;

	// Worker thread pool.
	private final ExecutorService worker;

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
	public BestiaZoneserver(ServiceFactory serviceFactory,
			BestiaConnectionInterface connection, String configFile) {

		// #### Config: Setup the config file.
		initilizeConfig(configFile);

		// #### Variable setup.
		worker = Executors.newFixedThreadPool(Integer.parseInt(config
				.getProperty("serverThreads")));

		this.messenger = new Messenger(connection, worker);
		this.cmdFactory = new CommandFactory(serviceFactory, messenger);
	}

	/**
	 * Ctor. For providing an external executor service. This is mostly for internal use for
	 * unit testing purposes to run the commands in the same thread as the unit
	 * tests. Should not be needed to be used in production.
	 * 
	 * @param serviceFactory
	 * @param connection
	 * @param configFile
	 * @param execService
	 */
	public BestiaZoneserver(ServiceFactory serviceFactory,
			BestiaConnectionInterface connection, String configFile,
			ExecutorService execService) {

		// #### Config: Setup the config file.
		initilizeConfig(configFile);

		// #### Variable setup.
		worker = execService;

		this.messenger = new Messenger(connection, worker);
		this.cmdFactory = new CommandFactory(serviceFactory, messenger);
	}

	private void initilizeConfig(String configFile) {
		try {
			config.load(new FileReader(configFile));
		} catch (IOException ex) {
			log.error("Can not read from config file: {}. Stopping.",
					configFile);
			System.exit(1);
		}
	}

	/**
	 * Starts the server. Initializes all the messaging pipeline, database
	 * connections, cache and scripts.
	 */
	public void start() throws Exception {

		if (isRunning) {
			log.warn("Bestia server is already running.");
			return;
		}

		// Initializing all messaging components.
		log.info("Bestia Behemoth is starting up!");

		// Create ScriptInitWorker: Reading and compiling all the scripts.
		log.info("Initializing: scripts...");
		ScriptInitWorker siworker = new ScriptInitWorker();
		siworker.run();

		// Create ActorInitWorker: Spawning and initializing all Actors.
		log.info("Initializing: actors...");

		log.info("Initializing: message queue...");

		log.info("Registering with Interserver...");

		isRunning = true;
		log.info("Bestia Behemoth Zone [{}] has started.", config.getProperty("name"));
	}

	/**
	 * Ceases all server operation and persists all pending data.
	 */
	public void stop() {
		stop(false);
	}

	/**
	 * Ceases all server operation and persists all pending data.
	 * 
	 * @param waitLong
	 *            If TRUE the server will wait for a far longer timeout until it
	 *            terminates all pending commands. Nice for debugging.
	 */
	public void stop(boolean waitLong) {
		isRunning = false;

		log.info("Bestia Behemoth Server is stopping...");
		log.info("Unsubscribe from Interserver...");

		// Shut down all the msg queues.
		log.info("Shutting down: command and messaging system...");
		worker.shutdown();
		log.info("Shutting down: actor subsystem...");

		// Wait for all threads to cease operation.
		try {
			if (waitLong) {
				worker.awaitTermination(10, TimeUnit.MINUTES);
			} else {
				worker.awaitTermination(5, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			// no op.
		}

		log.info("Zone: [{}] went down.", config.getProperty("name"));
	}

	/**
	 * Let the Server handle one message. It wraps the message asynchronously in
	 * a command with a given context so the command in turn can be executed
	 * asynchronously aswell. The resulting messages are send out over the
	 * connections to the clients.
	 * 
	 * @param message
	 * @return
	 */
	public void handleMessage(Message message) {

		// Create future command.
		final FetchCommandTask task = new FetchCommandTask(cmdFactory, message);
		final Future<Command> f = worker.submit(task);

		worker.execute(new Runnable() {

			@Override
			public void run() {
				try {
					worker.execute(f.get());
				} catch (InterruptedException | ExecutionException e) {
					log.error("Could not execute command.", e);
				}
			}
		});
	}
}
