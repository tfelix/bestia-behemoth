package net.bestia.interserver;

import net.bestia.messages.Message;

import org.junit.Assert;
import org.junit.Test;


public class InterserverConnectionFactoryTest {

	@Test
	public void get_publisher_test() {
		InterserverConnectionFactory fac = new InterserverConnectionFactory(1, "localhost", 6000, 6001);
		InterserverPublisher pub = fac.getPublisher();
		Assert.assertNotNull(pub);
	}
	
	@Test
	public void get_subscriber_test() {
		InterserverConnectionFactory fac = new InterserverConnectionFactory(1, "localhost", 6000, 6001);
		InterserverSubscriber sub = fac.getSubscriber(new InterserverMessageHandler() {
			
			@Override
			public void onMessage(Message msg) {
				
			}
		});
		Assert.assertNotNull(sub);
	}
}
