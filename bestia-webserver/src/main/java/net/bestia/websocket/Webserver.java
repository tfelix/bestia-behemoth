package net.bestia.websocket;

import java.io.IOException;

import net.bestia.connect.InterserverConnection;
import net.bestia.connect.InterserverConnection.InterserverConnectionHandler;
import net.bestia.messages.Message;
import net.bestia.util.BestiaConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;

public final class Webserver implements InterserverConnectionHandler {

	private static final Logger log = LogManager.getLogger(Webserver.class);

	private final InterserverConnection connection;
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
		this.connection = new InterserverConnection(name, this, publishUrl);
	}

	public void start() throws Exception {
		log.info("Starting the Bestia Behemoth Websocket Server...");

		Config.Builder b = new Config.Builder();
		b.host("0.0.0.0").port(8080);

		Nettosphere server = new Nettosphere.Builder().config(b.build()).build();
		server.start();

		log.info("Webserver started.");

		log.debug("Try to connect to interserver...");
		connection.connect();
	}

	public static void main(String[] args) {
		
		final BestiaConfiguration config = new BestiaConfiguration();
		try {
			config.load();
		} catch(IOException ex) {
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

	@Override
	public void onMessage(Message msg) {
		log.trace("Received message: {}", msg.toString());
	}

	@Override
	public void connectionLost() {
		log.debug("Connection to interserver lost.");
	}

}
