package net.bestia.webserver;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.util.BestiaConfiguration;

public final class Main {
	
	private static final Logger LOG = LogManager.getLogger(Main.class);

	public static void main(String[] args) {
		final BestiaConfiguration config = new BestiaConfiguration();
		try {
			config.load();
		} catch (IOException ex) {
			LOG.fatal("Could not load configuration file. Exiting.", ex);
			System.exit(1);
		}

		final Webserver server = new Webserver(config);
		try {
			server.start();
		} catch (Exception ex) {
			LOG.fatal("Server could not start.", ex);
			System.exit(1);
		}

		// Cancel the loginserver gracefully when the VM shuts down. Does not
		// work properly on windows machines.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				server.stop();
			}
		});
	}

}
