package net.bestia.websocket;

import java.io.IOException;

import net.bestia.interserver.InterserverConnectionFactory;
import net.bestia.interserver.InterserverPublisher;
import net.bestia.interserver.InterserverSubscriber;
import net.bestia.util.BestiaConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;

public final class Webserver {

	private static final Logger log = LogManager.getLogger(Webserver.class);

	private final InterserverPublisher publisher;
	private final InterserverSubscriber subscriber;
	private final String name;

	/**
	 * Class which starts and runs the bestia web front server.
	 * 
	 * @param config
	 *            Loaded configuration file.
	 */
	public Webserver(BestiaConfiguration config) {
		this.name = config.getProperty("web.name");

		BestiaConnectionProvider.create();

		final int listenPort = config.getIntProperty("inter.listenPort");
		final int publishPort = config.getIntProperty("inter.publishPort");
		final String domain = config.getProperty("inter.domain");

		InterserverConnectionFactory interservConnectionFactory = new InterserverConnectionFactory(1, domain,
				publishPort, listenPort);

		this.publisher = interservConnectionFactory.getPublisher();
		this.subscriber = interservConnectionFactory.getSubscriber(BestiaConnectionProvider.getInstance());
		
		// Subscribe to special topics.
		subscriber.subscribe("web/all");

		BestiaConnectionProvider.getInstance().setup(publisher, subscriber);
	}

	public void start() throws Exception {
		log.info("Starting the Bestia Websocket Server [{}]...", name);

		// Connect to the interserver.
		publisher.connect();
		subscriber.connect();

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
