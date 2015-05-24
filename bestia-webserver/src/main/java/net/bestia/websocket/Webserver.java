package net.bestia.websocket;


import net.bestia.util.BestiaConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;

public final class Webserver {

	private static final Logger log = LogManager.getLogger(Webserver.class);

	private final BestiaConfiguration config;
	

	public Webserver() {
		this.config = new BestiaConfiguration();
	}

	public void start() throws Exception {

		// File configFile = new File(App.class.getClassLoader().getResource("bestia.properties").toURI());

		log.info("Starting the Bestia Behemoth Websocket Server...");
		config.load();

		Config.Builder b = new Config.Builder();
		b.host("0.0.0.0").port(8080);

		Nettosphere server = new Nettosphere.Builder().config(b.build()).build();
		server.start();

		log.info("Webserver started.");

		log.debug("Try to connect to interserver...");
		//interconnection = new WebserverInterconnection(config);
		//interconnection.connect();
	}

	public static void main(String[] args) {
		final Webserver server = new Webserver();
		try {
			server.start();
		} catch (Exception ex) {
			log.fatal("Server could not start.", ex);
			System.exit(1);
		}
	}

}
