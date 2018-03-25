package net.bestia.zoneserver.service;

import com.hazelcast.core.HazelcastInstance;
import net.bestia.HazelMock;
import net.bestia.zoneserver.client.LatencyService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LatencyServiceTest {

	private final static HazelcastInstance hz = HazelMock.hazelcastMock();
	private static final long ACC_ID = 789;
	
	private LatencyService latencyService;
	
	@Before
	public void setup() {
		latencyService = new LatencyService(hz);
	}

	@Test
	public void addLatency_stampSet_ok() {
		long stamp = System.currentTimeMillis();
		latencyService.addLatency(123, stamp, stamp + 100);
	}

	@Test(expected = IllegalStateException.class)
	public void getClientLatency_noAccount_throws() {
		latencyService.getClientLatency(987);
	}

	@Test
	public void getClientLatency_accountLatencyAdded_ok() {
		long stamp = System.currentTimeMillis();
		latencyService.addLatency(ACC_ID, stamp, stamp + 150);
		int latency = latencyService.getClientLatency(ACC_ID);
		Assert.assertEquals(150, latency);
	}
}