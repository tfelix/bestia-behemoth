package net.bestia.core;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import net.bestia.core.command.Command;
import net.bestia.core.command.CommandFactory;
import net.bestia.core.connection.BestiaConnectionManager;
import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.game.worker.ActorInitWorker;
import net.bestia.core.game.worker.ActorLogicWorker;
import net.bestia.core.game.worker.MessageWorker;
import net.bestia.core.game.worker.ScriptInitWorker;
import net.bestia.core.message.Message;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * BestiaServer is the central instance to start and host a bestia browser game.
 * It is responsible for starting up the server, compiling the scripts, starting
 * the worker threads to work on messages.
 * 
 * The basic Bestia API works by getting messages from the user, deserializing
 * it to bestia messages and creating commands out of them. These commands will
 * be queued and worked by the worker threads generating one or more response
 * messages which will be in turn send by the messaging infrastructure back to
 * the user.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
public final class BestiaServer {
	private final static Logger log = LogManager.getLogger(BestiaServer.class);
	
	/**
	 * Utility class to combine the thread with the worker.
	 * 
	 * @author Thomas
	 *
	 * @param <T>
	 */
	private class WorkerThread<T> {
		public T worker;
		public Thread thread;
	}

	private CommandFactory cmdFactory;
	private LinkedBlockingQueue<Message> messageQueue;
	private BestiaConnectionManager connection;
	private Properties config = new Properties();
	private boolean isRunning = false;
	
	// All worker threads.
	private ExecutorService commandExecutor;
	private WorkerThread<MessageWorker> messageWorker = new WorkerThread<>();
	private List<WorkerThread<ActorLogicWorker>> actorLogicWorkers = new ArrayList<>();

	/**
	 * Ctor. The server needs a connection to its clients so it can use the
	 * messaging API to communicate with them.
	 * 
	 * @param serviceFactory Service factory to getting the game beans from a datasource.
	 * @param connection Manager to hold the connections to the clients. 
	 * @param configFile File with config settings for the bestia server.
	 */
	public BestiaServer(
			ServiceFactory serviceFactory,
			BestiaConnectionManager connection, 
			String configFile) {

		this.connection = connection;
		
		setupDefaultIni();

		// #### Config: Setup the config file. 
		try {
			config.load(new FileReader(configFile));
		} catch (IOException ex) {
			log.error("Can not read from config file: {}. Using defaults.", configFile);
		}
		
		// #### Variable setup.
		commandExecutor = Executors.newFixedThreadPool(
				Integer.parseInt(config.getProperty("CommandExecThreads")));
		messageQueue = new LinkedBlockingQueue<Message>();
		serviceFactory.setMessageQueue(messageQueue);
		cmdFactory = new CommandFactory(serviceFactory, messageQueue, connection);
		
	}
	
	private void setupDefaultIni() {
		config.setProperty("CommandExecThreads", "1");
		config.setProperty("ActorThreads", "1");
		config.setProperty("MessageThreadCount", "1");
	}

	/**
	 * Let the Server handle one message. It returns a list of messages which in
	 * turn must be send to the clients.
	 * 
	 * @param message
	 * @return
	 */
	public synchronized void handleMessage(Message message) {
		if (isRunning() == false) {
			throw new IllegalStateException("Server is not running.");
		}

		Command cmd = cmdFactory.getCommand(message);
		log.trace("Command created: {}", cmd.toString());
		commandExecutor.execute(cmd);
	}

	/**
	 * Starts the server. Initializes all the messaging pipeline, database
	 * connections, cache and scripts.
	 */
	public synchronized void start() throws Exception {
		
		if(isRunning) {
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
		ActorInitWorker aiWorker = new ActorInitWorker();
		aiWorker.run();
		
		// Create ActorLogicWorker: KI of Actors.
		final int nActorThreads = Integer.parseInt(config.getProperty("ActorThreads"));
		actorLogicWorkers = new ArrayList<>();
		for(int i = 0; i < nActorThreads; i++) {
			// Create the worker thread and initialize it.
			WorkerThread<ActorLogicWorker> actorWorker = new WorkerThread<>();
			actorWorker.worker = new ActorLogicWorker();
			actorWorker.thread = new Thread(actorWorker.worker);
			actorLogicWorkers.add(actorWorker);
			actorWorker.thread.start();
		}
		

		log.info("Initializing: message queue...");
		messageWorker.worker = new MessageWorker(connection, messageQueue);
		messageWorker.thread = new Thread(messageWorker.worker);
		messageWorker.thread.start();
		
		isRunning = true;
		log.info("Bestia Behemoth has started.");
	}

	/**
	 * Ceases all server operation and persists all pending data.
	 */
	public synchronized void stop() {
		isRunning = false;
		
		log.info("Bestia Behemoth Server is stopping...");
		// Shut down all the msg queues.
		log.info("Shutting down: command system...");
		commandExecutor.shutdown();
		//commandExecutor.awaitTermination(10, TimeUnit.SECONDS);
		log.info("Shutting down: messaging...");
		log.info("Shutting down: actors...");
		for(WorkerThread<ActorLogicWorker> worker : actorLogicWorkers) {
			// Stop all the worker.
			worker.worker.stop();
		}
		
		// Wait for all threads to cease operation.
		try {
			messageWorker.thread.join();
		} catch (InterruptedException e) {
			// no op.
		}
	}

	/**
	 * Flag if the server is running.
	 * 
	 * @return
	 */
	public synchronized boolean isRunning() {
		return isRunning;
	}
}
