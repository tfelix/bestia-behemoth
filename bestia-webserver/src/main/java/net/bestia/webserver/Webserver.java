package net.bestia.webserver;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;

import net.bestia.interserver.InterserverConnectionFactory;
import net.bestia.interserver.InterserverMessageHandler;
import net.bestia.interserver.InterserverPublisher;
import net.bestia.interserver.InterserverSubscriber;
import net.bestia.messages.Message;
import net.bestia.util.BestiaConfiguration;

public final class Webserver {

	private static final Logger log = LogManager.getLogger(Webserver.class);

	private final InterserverConnectionFactory interConnectionFactory;
	private final InterserverPublisher publisher;
	private final InterserverSubscriber subscriber;

	private final String name;
	private final BestiaConfiguration config;
	private final int port;
	private Nettosphere server;
	
	public static BestiaConnectionProvider provider;

	/**
	 * Class which starts and runs the bestia web front server.
	 * 
	 * @param config
	 *            Loaded configuration file.
	 */
	public Webserver(BestiaConfiguration config) {

		if (config == null || !config.isLoaded()) {
			throw new IllegalArgumentException("Config is null or not loaded.");
		}

		this.name = config.getProperty("web.name");
		this.config = config;
		this.port = config.getIntProperty("web.port");

		final int listenPort = config.getIntProperty("inter.listenPort");
		final int publishPort = config.getIntProperty("inter.publishPort");
		final String domain = config.getProperty("inter.domain");

		this.interConnectionFactory = new InterserverConnectionFactory(1, domain, publishPort, listenPort);

		this.publisher = interConnectionFactory.getPublisher();
		this.subscriber = interConnectionFactory.getSubscriber(new InterserverMessageHandler() {
			
			@Override
			public void onMessage(Message msg) {
				provider.onMessage(msg);
			}
		});
		
		Webserver.provider = new BestiaConnectionProvider(publisher, subscriber);
		
		// Subscribe to special topics.
		subscriber.subscribe("web/all");
	}

	public void start() throws IOException {
		log.info(config.getVersion());
		log.info("Starting the Bestia Websocket Server [{}]...", name);

		// Connect to the interserver.
		publisher.connect();
		subscriber.connect();

		final Config.Builder b = new Config.Builder();
		b.host("0.0.0.0").port(port);

		server = new Nettosphere.Builder().config(b.build()).build();
		server.start();
		log.info("Webserver [{}] started. Listening on port: {}", name, port);
	}

	public void stop() {
		log.info("Stopping the Bestia Websocket Server [{}]...", name);
		server.stop();
		
		interConnectionFactory.shutdown();

		log.info("Websocket Server [{}] stopped.", name);
	}
}
