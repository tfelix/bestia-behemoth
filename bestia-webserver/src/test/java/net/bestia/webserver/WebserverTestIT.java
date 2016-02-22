package net.bestia.webserver;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import net.bestia.util.BestiaConfiguration;

/**
 * Checks basic operation of the webserver.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class WebserverTestIT {

	@Test
	public void startStop_noException() throws IOException {

		final ClassLoader classLoader = WebserverTestIT.class.getClassLoader();
		final File configFile = new File(classLoader.getResource("webserverTest.properties").getFile());
		
		final BestiaConfiguration config = new BestiaConfiguration();
		config.load(configFile);

		final Webserver server = new Webserver(config);

		server.start();
		server.stop();
	}
}
