package net.bestia.zoneserver.service;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class LatencyServiceTest {

	@Autowired
	private LatencyService latencyService;

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
		latencyService.addLatency(123, 10000000, 10000000);
	}

	@Test
	public void addLatency_stampSet_ok() {
		long stamp = latencyService.getTimestamp(123);
		latencyService.addLatency(123, stamp + 100, stamp);
	}

	@Test(expected = IllegalStateException.class)
	public void getClientLatency_noAccount_throws() {
		latencyService.getClientLatency(123);
	}

	@Test
	public void getClientLatency_accountLatencyAdded_ok() {
		long stamp = latencyService.getTimestamp(123);
		latencyService.addLatency(123, stamp + 150, stamp);
		int latency = latencyService.getClientLatency(123);
		Assert.assertEquals(150, latency);
	}

}
