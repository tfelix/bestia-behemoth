package net.bestia.interserver;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.Message;
import net.bestia.messages.PingMessage;
import net.bestia.util.BestiaConfiguration;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ZMQPubSubTest {
	private static Interserver server;
	private static BestiaConfiguration config;
	private static int listenPort;
	private static int publishPort;
	private static String domain;
	
	@BeforeClass
	public static void setUp() throws IOException {
		config = new BestiaConfiguration();
		config.load();
		server = new Interserver(config);
		server.start();
		
		// Get url.
		domain = config.getProperty("inter.domain");
		listenPort = config.getIntProperty("inter.publishPort");
		publishPort = config.getIntProperty("inter.listenPort");
	}
	
	@AfterClass
	public static void teardown() {
		server.stop();
	}
	
	@Test
	public void topic_test() throws IOException {
		InterserverConnectionFactory fac = new InterserverConnectionFactory(1, domain, listenPort, publishPort);
		InterserverPublisher pub = fac.getPublisher();
		List<Message> msgs = new ArrayList<>();
		InterserverSubscriber sub = fac.getSubscriber(new InterserverMessageHandler() {
			
			@Override
			public void onMessage(Message msg) {
				msgs.add(msg);
			}
			
			@Override
			public void connectionLost() {
				
			}
		});
		
		pub.connect();
		sub.connect();
		
		PingMessage ping = new PingMessage();
		pub.publish(ping);
		assertTrue("No received message since no subscribed topic.", msgs.size() == 0);
		
		sub.subscribe("test");
		pub.publish(ping);
		assertTrue("No message should arrive via wrong subscribed topic.", msgs.size() == 0);
		
		sub.subscribe(ping.getMessagePath());
		pub.publish(ping);
		assertTrue("Message should arrive via right subscribed topic.", msgs.size() == 1);
		
		sub.unsubscribe(ping.getMessagePath());
		pub.publish(ping);
		assertTrue("No Message should arrive via unsubscribed topic.", msgs.size() == 1);
		
		
		pub.disconnect();
		sub.disconnect();
	}
}
