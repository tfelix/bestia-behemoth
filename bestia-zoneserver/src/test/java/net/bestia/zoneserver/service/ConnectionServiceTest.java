package net.bestia.zoneserver.service;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.MultiMap;

import akka.actor.ActorSystem;
import akka.actor.Terminated;
import akka.testkit.TestProbe;
import net.bestia.testing.BasicMocks;
import scala.concurrent.Future;

@RunWith(MockitoJUnitRunner.class)
public class ConnectionServiceTest {

	private static final long CONNECTED_ACC_ID = 10;
	private static final long NOT_CONNECTED_ACC_ID = 11;
	private BasicMocks mocks = new BasicMocks();
	private HazelcastInstance hz = mocks.hazelcastMock();
	
	private ActorSystem sys1;
	private ActorSystem sys2;

	private ConnectionService conSrv;
	
	private TestProbe webserver1;
	private TestProbe webserver2;
	
	@Mock
	private MultiMap<String, Long> clients;

	@Before
	public void setup() {
		conSrv = new ConnectionService(hz);
		
		sys1 = mocks.actorSystemByName("test1");
		sys2 = mocks.actorSystemByName("test2");
		
		webserver1 = new TestProbe(sys1, "webserver1");
		webserver2 = new TestProbe(sys2, "webserver2");
	}
	
	@After
	public void teardown() throws InterruptedException {
		Future<Terminated> t1 = sys1.terminate();
		Future<Terminated> t2 = sys2.terminate();
		
		while(!t1.isCompleted() && !t2.isCompleted()) {
			Thread.sleep(100);
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void addClient_negAccId_throws() {
		conSrv.connected(-13, webserver1.ref().path().address());
	}

	@Test(expected = NullPointerException.class)
	public void addClient_nullPath_throws() {
		conSrv.connected(13, null);
	}

	@Test
	public void addClient_posIdNotNullPath_works() {
		conSrv.connected(13, webserver1.ref().path().address());
		Assert.assertTrue(conSrv.isConnected(13));
	}

	@Test
	public void removeClient_validAccId_works() {
		conSrv.connected(15, webserver1.ref().path().address());
		conSrv.connected(16, webserver1.ref().path().address());
		conSrv.connected(25, webserver2.ref().path().address());
		conSrv.connected(26, webserver2.ref().path().address());
		
		conSrv.disconnectAccount(15);
		Assert.assertFalse(conSrv.isConnected(15));
		Assert.assertTrue(conSrv.isConnected(16));
		Assert.assertTrue(conSrv.isConnected(25));
		Assert.assertTrue(conSrv.isConnected(26));
		
		conSrv.disconnectedAllFromServer(webserver2.ref().path().address());
		Assert.assertTrue(conSrv.isConnected(16));
		Assert.assertFalse(conSrv.isConnected(25));
		Assert.assertFalse(conSrv.isConnected(26));
		
	}
	
	@Test
	public void isConnected_accIdNotOnline_false() {
		Assert.assertFalse(conSrv.isConnected(NOT_CONNECTED_ACC_ID));
	}
	
	@Test
	public void isConnected_accIdOnline_true() {
		conSrv.connected(CONNECTED_ACC_ID, webserver1.ref().path().address());
		Assert.assertTrue(conSrv.isConnected(CONNECTED_ACC_ID));
	}
}
