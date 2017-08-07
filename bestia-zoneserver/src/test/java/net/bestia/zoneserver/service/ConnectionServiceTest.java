package net.bestia.zoneserver.service;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.hazelcast.core.HazelcastInstance;

import akka.testkit.TestProbe;
import net.bestia.testing.BasicMocks;

public class ConnectionServiceTest {

	private static final long CONNECTED_ACC_ID = 10;
	private static final long NOT_CONNECTED_ACC_ID = 11;
	private BasicMocks mocks = new BasicMocks();
	private HazelcastInstance hz = mocks.hazelcastMock();

	private ConnectionService conSrv;
	private TestProbe webserver;

	@Before
	public void setup() {
		conSrv = new ConnectionService(hz);
		webserver = new TestProbe(mocks.actorSystem(), "webserver");
	}

	@Test(expected = IllegalArgumentException.class)
	public void addClient_negAccId_throws() {
		conSrv.connected(-13, webserver.ref().path().address());
	}

	@Test(expected = NullPointerException.class)
	public void addClient_nullPath_throws() {
		conSrv.connected(13, null);
	}

	@Test
	public void addClient_posIdNotNullPath_works() {
		conSrv.connected(13, webserver.ref().path().address());
		Assert.assertTrue(conSrv.isConnected(13));
	}


	@Test
	public void removeClient_validAccId_works() {
		conSrv.connected(13, webserver.ref().path().address());
		conSrv.disconnected(13);
		Assert.assertFalse(conSrv.isConnected(13));
	}
	
	@Test
	public void isOnline_accIdNotOnline_false() {
		Assert.assertFalse(conSrv.isConnected(NOT_CONNECTED_ACC_ID));
	}
	
	@Test
	public void isOnline_accIdOnline_true() {
		conSrv.connected(CONNECTED_ACC_ID, webserver.ref().path().address());
		Assert.assertTrue(conSrv.isConnected(CONNECTED_ACC_ID));
	}
}
