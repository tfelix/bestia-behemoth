package net.bestia.interserver;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.Message;
import net.bestia.messages.system.PingMessage;
import net.bestia.util.BestiaConfiguration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ZMQPubSubTest {
	private Interserver server;
	private BestiaConfiguration config;
	private int listenPort;
	private int publishPort;
	private String domain;

	private InterserverConnectionFactory fac;
	private InterserverPublisher pub;
	private InterserverSubscriber sub;

	private List<Message> msgs = new ArrayList<>();


	@Before
	public void setUp() throws IOException {
		config = new BestiaConfiguration();
		config.load();
		server = new Interserver(config);
		server.start();

		// Get url.
		domain = config.getProperty("inter.domain");
		listenPort = config.getIntProperty("inter.publishPort");
		publishPort = config.getIntProperty("inter.listenPort");

		msgs.clear();
		
		fac = new InterserverConnectionFactory(1, domain, listenPort, publishPort);
		
		pub = fac.getPublisher();
		sub = fac.getSubscriber(new InterserverMessageHandler() {

			@Override
			public void onMessage(Message msg) {
				msgs.add(msg);
			}
		});
	}

	@After
	public void teardown() {				
		sleep(200);
		server.stop();
		fac.shutdown();
		
	}

	@Test
	public void not_subscribed_test() throws IOException {

		PingMessage ping = new PingMessage();
		pub.publish(ping);
		assertTrue("No received message since no subscribed topic.", msgs.size() == 0);

		sleep(200);
	}

	@Test
	public void wrong_subscribed_topic_test() throws IOException {

		PingMessage ping = new PingMessage();
		sub.subscribe("test");
		pub.publish(ping);

		// Dont close too early.
		sleep(200);

		assertTrue("No message should arrive via wrong subscribed topic.", msgs.size() == 0);
	}

	@Test
	public void right_subscribed_topic_test() throws IOException {

		PingMessage ping = new PingMessage();
		sub.subscribe(ping.getMessagePath());
		sleep(100);
		
		pub.publish(ping);

		// Dont close too early.
		sleep(200);

		assertTrue("Message should arrive via subscribed topic.", msgs.size() == 1);

	}

	@Test
	public void unsubscribed_topic_test() throws IOException {

		PingMessage ping = new PingMessage();
		sub.subscribe(ping.getMessagePath());
		sub.unsubscribe(ping.getMessagePath());
		pub.publish(ping);

		// Dont close too early.
		sleep(200);

		assertTrue("No Message should arrive via unsubscribed topic.", msgs.size() == 0);
	}
	
	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// no op.
		}
	}
}
