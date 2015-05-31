package net.bestia.websocket;

import java.io.IOException;

import net.bestia.interserver.InterserverConnectionFactory;
import net.bestia.interserver.InterserverPublisher;
import net.bestia.util.BestiaConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;

public final class Webserver {

	final static class InterserverConnectionProvider {
		private static InterserverConnectionProvider instance = null;

		private InterserverPublisher publisher;

		private InterserverConnectionProvider(InterserverPublisher publisher) {
			this.publisher = publisher;
		}

		public InterserverPublisher getConnection() {
			return publisher;
		}

		public static void setup(InterserverPublisher publisher) {
			instance = new InterserverConnectionProvider(publisher);
		}

		/**
		 * Static getter of the InterserverConnectionProvider so it can be retrieved for the socket handler.
		 * 
		 * @return Instance of the InterserverConnectionProvider
		 */
		public static InterserverConnectionProvider getInstance() {
			if (instance == null) {
				throw new IllegalStateException("setup() must be called bevor invoking these method.");
			}
			return instance;
		}

	}

	private static final Logger log = LogManager.getLogger(Webserver.class);

	private final InterserverPublisher connection;
	private final String name;

	/**
	 * Class which starts and runs the bestia web front server.
	 * 
	 * @param config
	 *            Loaded configuration file.
	 */
	public Webserver(BestiaConfiguration config) {
		this.name = config.getProperty("web.name");

		// Create the publish url.
		String publishUrl = config.getDomainPortString("web.domain", "web.publishPort", "tcp://");
		
		InterserverConnectionFactory interservConnectionFactory = new InterserverConnectionFactory(1);
		
		this.connection = interservConnectionFactory.getPublisher(publishUrl);
		// Setup the provider in this static way so the handler can access it.
		InterserverConnectionProvider.setup(connection);
	}

	public void start() throws Exception {
		log.info("Starting the Bestia Websocket Server [{}]...", name);

		// Connect to the interserver.
		connection.connect();

		Config.Builder b = new Config.Builder();
		b.host("0.0.0.0").port(8080);

		Nettosphere server = new Nettosphere.Builder().config(b.build()).build();
		server.start();
		log.info("Webserver [{}] started.", name);
	}

	public static void main(String[] args) {

		final BestiaConfiguration config = new BestiaConfiguration();
		try {
			config.load();
		} catch (IOException ex) {
			log.fatal("Could not load configuration file. Exiting.", ex);
			System.exit(1);
		}

		final Webserver server = new Webserver(config);
		try {
			server.start();
		} catch (Exception ex) {
			log.fatal("Server could not start.", ex);
			System.exit(1);
		}
	}
}
