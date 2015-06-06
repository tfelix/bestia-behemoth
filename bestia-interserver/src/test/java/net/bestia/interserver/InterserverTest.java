package net.bestia.interserver;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.Message;
import net.bestia.messages.PingMessage;
import net.bestia.util.BestiaConfiguration;

import org.junit.Assert;
import org.junit.Test;

public class InterserverTest {

	@Test
	public void start_stop_test() throws Exception {
		BestiaConfiguration config = new BestiaConfiguration();
		config.load();
		Interserver server = new Interserver(config);
		server.start();
		server.stop();
	}

	@Test(expected = IllegalArgumentException.class)
	public void not_loaded_config_test() {
		BestiaConfiguration config = new BestiaConfiguration();
		Interserver server = new Interserver(config);
		server.start();

	}

	@Test
	public void messaging_test() throws Exception {
		BestiaConfiguration config = new BestiaConfiguration();
		Interserver server = new Interserver(config);
		server.start();

		InterserverConnectionFactory conFac = new InterserverConnectionFactory(
				1, "localhost", config.getIntProperty("inter.listenPort"),
				config.getIntProperty("inter.publishPort"));
		final List<Message> messages = new ArrayList<Message>();
		conFac.getSubscriber(new InterserverMessageHandler() {

			@Override
			public void onMessage(Message msg) {
				if (msg instanceof PingMessage) {
					messages.add(msg);
				}
			}

			@Override
			public void connectionLost() {

			}
		});

		InterserverPublisher pub = conFac.getPublisher();
		PingMessage pingMsg = new PingMessage();
		pub.publish(pingMsg);

		Assert.assertEquals("One message should be saved in the list.", 1,
				messages.size());
	}
}
