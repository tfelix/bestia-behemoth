package net.bestia.interserver;

import net.bestia.messages.Message;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class InterserverConnectionFactoryTest {

	@Test
	public void get_publisher_test() throws IOException, InterruptedException {
		InterserverConnectionFactory fac = new InterserverConnectionFactory(1, "localhost", 6000, 6001);
		InterserverPublisher pub = fac.getPublisher();
		Assert.assertNotNull(pub);
		Thread.sleep(100);
		fac.shutdown();
	}

	@Test
	public void get_subscriber_test() throws IOException, InterruptedException {
		InterserverConnectionFactory fac = new InterserverConnectionFactory(1, "localhost", 6000, 6001);
		InterserverSubscriber sub = fac.getSubscriber(new InterserverMessageHandler() {
			@Override
			public void onMessage(Message msg) {

			}
		});
		Assert.assertNotNull(sub);
		Thread.sleep(100);
		fac.shutdown();
	}
}
