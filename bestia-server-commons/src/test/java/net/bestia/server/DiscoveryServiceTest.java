package net.bestia.server;

import org.junit.Assert;
import org.junit.Test;

import akka.actor.Address;
import net.bestia.testing.BasicMocks;

public class DiscoveryServiceTest {

	private DiscoveryService discServ;

	public void setup() {
		BasicMocks mocks = new BasicMocks();
		discServ = new DiscoveryService(mocks.hazelcastMock());
	}

	@Test
	public void shouldJoindAsSeedNode_noOtherClusterNodes_true() {
		Assert.assertTrue(discServ.shoudJoinAsSeedNode());
	}

	@Test
	public void shouldJoindAsSeedNode_moreNodesThenNumSeedNodes_false() {

		for(int i = 0; i <= DiscoveryService.NUM_SEED_NODES; i++) {
			Address addr = new Address("akka.tcp", "System" + i);
			discServ.addClusterNode(addr);
		}
		
		Assert.assertFalse(discServ.shoudJoinAsSeedNode());
	}

	@Test
	public void getClusterSeedNodes_listOfSeedNodes() {
		Address addr = new Address("akka.tcp", "System12");
		discServ.addClusterNode(addr);
		
		Assert.assertEquals(1, discServ.getClusterSeedNodes().size());
		Assert.assertTrue(discServ.getClusterSeedNodes().contains(addr));
	}

	@Test(expected = NullPointerException.class)
	public void addClusterNode_null_throws() {
		discServ.addClusterNode(null);
	}

	@Test(expected = NullPointerException.class)
	public void removeClusterNode_null_throws() {
		discServ.removeClusterNode(null);
	}

	@Test
	public void removeClusterNode_addClusterNode_validData_removed() {
		for(int i = 0; i <= DiscoveryService.NUM_SEED_NODES; i++) {
			Address addr = new Address("akka.tcp", "System" + i);
			discServ.addClusterNode(addr);
			discServ.removeClusterNode(addr);
		}
		
		Assert.assertEquals(0, discServ.getClusterSeedNodes().size());
		Assert.assertTrue(discServ.shoudJoinAsSeedNode());
	}

}
