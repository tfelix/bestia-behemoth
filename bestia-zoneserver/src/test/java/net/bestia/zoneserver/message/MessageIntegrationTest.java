package net.bestia.zoneserver.message;

import net.bestia.interserver.Interserver;
import net.bestia.interserver.InterserverConnectionFactory;
import net.bestia.messages.LoginMessage;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.Zoneserver;

import org.junit.Test;

public class MessageIntegrationTest {

	@Test
	public void login_sequence_test() {
		// Create a interserver.
		BestiaConfiguration config = new BestiaConfiguration();
		Interserver inter = new Interserver(config);
		inter.start();

		// Create zoneserver.
		Zoneserver zone = new Zoneserver();
		zone.start();

		// Conn
		InterserverConnectionFactory conFac = new InterserverConnectionFactory(
				1, "localhost", config.getIntProperty("inter.listenPort"),
				config.getIntProperty("inter.publishPort"));

		// Now send a auth token message to trigger a response.
		LoginMessage msg = new LoginMessage();
		

	}
}
