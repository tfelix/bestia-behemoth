package net.bestia.interserver;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.netflix.servo.monitor.Monitors;

import net.bestia.util.BestiaConfiguration;

public class Main {
	
	private static final Logger LOG = LogManager.getLogger(Main.class);

	public static void main(String[] args) {
		final BestiaConfiguration config = new BestiaConfiguration();
		try {
			config.load();
		} catch (IOException ex) {
			LOG.fatal("Could not load config file. Exiting.", ex);
			return;
		}

		final Interserver interserver = new Interserver(config);

		if (!interserver.start()) {
			LOG.fatal("Server could not start. Exiting.");
			return;
		}
		
		Monitors.registerObject("interserver", interserver);

		// Cancel the interserver gracefully when the VM shuts down. Does not
		// work properly on windows machines.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				interserver.stop();
			}
		});
	}

}
