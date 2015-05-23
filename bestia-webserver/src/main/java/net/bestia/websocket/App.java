package net.bestia.websocket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;

public class App {

	private static final Logger log = LogManager.getLogger(App.class);

	// TODO Config nicht hard coden sondern über die Start Argumente erhalten.
	public static void main(String[] args) {

		try {
			//File configFile = new File(App.class.getClassLoader().getResource("bestia.properties").toURI());

			log.info("Starting the Bestia Behemoth Websocket Server...");
			
			log.debug("Try to connect to interserver...");

			Config.Builder b = new Config.Builder();
			b.host("0.0.0.0").port(8080);

			Nettosphere server = new Nettosphere.Builder().config(b.build()).build();
			server.start();
			
			log.info("Server started.");

		} catch (Exception e) {
			log.fatal("Error while startup. Exit.", e);
		}
	}

}
