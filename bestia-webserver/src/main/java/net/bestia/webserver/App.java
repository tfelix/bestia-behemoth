package net.bestia.webserver;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import net.bestia.core.BestiaZoneserver;
import net.bestia.webserver.bestia.BestiaNettosphereConnection;

import org.atmosphere.nettosphere.Config;
import org.atmosphere.nettosphere.Nettosphere;

public class App {

	// TODO Config nicht hard coden sondern über die Start Argumente erhalten.
	public static void main(String[] args) {

		// Starting up the spring framework.
		// ApplicationContext ctx = new
		// ClassPathXmlApplicationContext("spring-config.xml");
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
				// TODO besseres exception handling.
				System.err.println("Can not start bestia zone. " + e.getMessage());
				System.exit(1);
			}

			Config.Builder b = new Config.Builder();

			List<String> paths = new ArrayList<String>();
			paths.add("C:\\xampp\\htdocs\\bestia\\img");

			b.resource("../bestia-www-client/source").host("0.0.0.0").port(8080);

			Nettosphere server = new Nettosphere.Builder().config(b.build())
					.build();
			server.start();
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		

	}

}
