package net.bestia.gmserver;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
	
	private static final Logger LOG = LogManager.getLogger(Main.class);

	public static void main(String[] args) {
		/*
		final BestiaConfiguration config = new BestiaConfiguration();
		try {
			config.load();
		} catch (IOException ex) {
			LOG.fatal("Could not load configuration file. Exiting.", ex);
			System.exit(1);
		}*/

		final GMServer server = new GMServer();

		if (!server.start()) {
			LOG.fatal("GM-Server could not start. Exiting.");
			System.exit(1);
		}

		// Cancel the server gracefully when the VM shuts down. 
		// Does not work properly on windows machines.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				server.stop();
			}
		});
	}

}
