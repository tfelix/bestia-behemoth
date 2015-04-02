package net.bestia.webserver;

import java.io.File;

import net.bestia.core.BestiaZoneserver;
import net.bestia.webserver.bestia.BestiaNettosphereConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;

public class App {
	
	private static final Logger log = LogManager.getLogger(App.class);

	// TODO Config nicht hard coden sondern über die Start Argumente erhalten.
	public static void main(String[] args) {

		try {
			File configFile = new File(App.class.getClassLoader()
					.getResource("bestia.properties").toURI());
			
			final BestiaZoneserver bestiaZone = new BestiaZoneserver(
					BestiaNettosphereConnection.getInstance(), configFile.toString());
			BestiaNettosphereConnection.getInstance().setZone(bestiaZone);
			
			// Start the zone.
			try {
				bestiaZone.start();
			} catch (Exception e) {
				log.fatal("Could not start bestia zone server.", e);
				System.exit(1);
			}

			Config.Builder b = new Config.Builder();

			//List<String> paths = new ArrayList<String>();
			//paths.add("C:\\xampp\\htdocs\\bestia\\img");
			//b.resource("../bestia-www-client/source").host("0.0.0.0").port(8080);
			b.host("0.0.0.0").port(8080);

			Nettosphere server = new Nettosphere.Builder().config(b.build())
					.build();
			server.start();
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		

	}

}
