package net.bestia.zoneserver.service;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.zoneserver.TestConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { TestConfiguration.class, LatencyServiceTest.ContextConfiguration.class })
public class LatencyServiceTest {

	@Configuration
	static class ContextConfiguration {

		// this bean will be injected into the OrderServiceTest class
		@Bean
		public LatencyService latencyService(HazelcastInstance hz) {
			LatencyService service = new LatencyService(hz);
			// set properties, etc.
			return service;
		}
	}

	@Autowired
	private LatencyService latencyService;

	@Test(expected = NullPointerException.class)
	public void ctor_null_throws() {
		new LatencyService(null);
	}

	/*
	 * @Test public void getTimestamp_id_newTimestamp() { LatencyService src =
	 * new LatencyService(hz); long stamp = src.getTimestamp(123);
	 * Assert.assertNotEquals(0, stamp); }
	 * 
	 * @Test(expected = IllegalStateException.class) public void
	 * addLatency_noStampSet_throws() { LatencyService src = new
	 * LatencyService(hz); src.addLatency(123, 10000000, 10000000); }
	 * 
	 * @Test public void addLatency_stampSet_ok() { LatencyService src = new
	 * LatencyService(hz); long stamp = src.getTimestamp(123);
	 * src.addLatency(123, stamp + 100, stamp); }
	 * 
	 * @Test(expected = IllegalStateException.class) public void
	 * getClientLatency_noAccount_throws() { LatencyService src = new
	 * LatencyService(hz); src.getClientLatency(123); }
	 * 
	 * @Test public void getClientLatency_accountLatencyAdded_ok() {
	 * LatencyService src = new LatencyService(hz); long stamp =
	 * src.getTimestamp(123); src.addLatency(123, stamp + 150, stamp); int
	 * latency = src.getClientLatency(123); Assert.assertEquals(150, latency); }
	 */

}
