package net.bestia.zoneserver.service;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.zoneserver.BasicMocks;

public class LatencyServiceTest {

	private final static BasicMocks mocks = new BasicMocks();
	private final static HazelcastInstance hz = mocks.hazelcastMock();
	
	private LatencyService latencyService;
	
	@Before
	public void setup() {
		latencyService = new LatencyService(hz);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_null_throws() {
		new LatencyService(null);
	}

	@Test
	public void getTimestamp_id_newTimestamp() {
		long stamp = latencyService.getTimestamp(123);
		Assert.assertNotEquals(0, stamp);
	}

	@Test(expected = IllegalStateException.class)
	public void addLatency_noStampSet_throws() {
		latencyService.addLatency(123456, 10000000, 10000000);
	}

	@Test
	public void addLatency_stampSet_ok() {
		long stamp = latencyService.getTimestamp(123);
		latencyService.addLatency(123, stamp + 100, stamp);
	}

	@Test(expected = IllegalStateException.class)
	public void getClientLatency_noAccount_throws() {
		latencyService.getClientLatency(987);
	}

	@Test
	public void getClientLatency_accountLatencyAdded_ok() {
		long stamp = latencyService.getTimestamp(789);
		latencyService.addLatency(789, stamp + 150, stamp);
		int latency = latencyService.getClientLatency(789);
		Assert.assertEquals(150, latency);
	}

}
