package net.bestia.loginserver;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.util.BestiaConfiguration;

/**
 * Cental entrance point of the app.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
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

		final Loginserver server = new Loginserver(config);

		if (!server.start()) {
			LOG.fatal("Server could not start. Exiting.");
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
