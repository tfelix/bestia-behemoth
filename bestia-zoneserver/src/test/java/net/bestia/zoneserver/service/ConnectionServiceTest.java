package net.bestia.zoneserver.service;

import java.util.Collection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.hazelcast.core.HazelcastInstance;

import akka.actor.Address;
import akka.testkit.TestProbe;
import net.bestia.zoneserver.BasicMocks;

public class ConnectionServiceTest {

	private BasicMocks mocks = new BasicMocks();
	private HazelcastInstance hz = mocks.hazelcast();

	private ConnectionService conSrv;
	private TestProbe webserver;
	private TestProbe webserverSecond;

	@Before
	public void setup() {
		conSrv = new ConnectionService(hz);
		webserver = new TestProbe(mocks.actorSystem(), "webserver");
		webserverSecond = new TestProbe(mocks.actorSystem("second-test-cluster"), "webserver_second");
	}

	@Test(expected = IllegalArgumentException.class)
	public void addClient_negAccId_throws() {
		conSrv.addClient(-13, webserver.ref().path());
	}

	@Test(expected = NullPointerException.class)
	public void addClient_nullPath_throws() {
		conSrv.addClient(13, null);
	}

	@Test
	public void addClient_posIdNotNullPath_works() {
		conSrv.addClient(13, webserver.ref().path());
		Assert.assertEquals(webserver.ref().path(), conSrv.getPath(13));
	}

	@Test(expected = NullPointerException.class)
	public void getClients_nullAddress_throws() {
		conSrv.getClients(null);
	}

	@Test
	public void getClients_notNullAddress_works() {
		conSrv.addClient(13, webserver.ref().path());
		Address addr = webserver.ref().path().address();
		Collection<Long> connected = conSrv.getClients(addr);
		Assert.assertTrue(connected.contains(13L));
	}

	@Test
	public void removeClient_validAccId_works() {
		conSrv.addClient(13, webserver.ref().path());
		conSrv.removeClient(13);
		Assert.assertNull(conSrv.getPath(13));
	}

	@Test(expected = NullPointerException.class)
	public void removeClients_null_throws() {
		conSrv.removeClients(null);
	}

	@Test
	public void removeClients_validAddr_works() {
		conSrv.addClient(13, webserver.ref().path());
		conSrv.addClient(14, webserver.ref().path());
		conSrv.addClient(15, webserverSecond.ref().path());
		
		Address addr = webserver.ref().path().address();
		conSrv.removeClients(addr);
		
		Assert.assertNull(conSrv.getPath(13));
		Assert.assertNull(conSrv.getPath(14));
		Assert.assertNotNull(conSrv.getPath(15));
	}

	@Test
	public void getPath_unknownId_null() {
		Assert.assertNull(conSrv.getPath(1345));
	}

	@Test
	public void getPath_knownId_path() {
		conSrv.addClient(12, webserver.ref().path());
		Assert.assertNotNull(conSrv.getPath(12));
	}
}
