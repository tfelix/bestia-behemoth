package net.bestia.server;

import org.junit.Assert;
import org.junit.Test;


public class AkkaClusterTest {
	
	@Test(expected = NullPointerException.class)
	@SuppressWarnings("all")
	public void getNodeName_null_throws() {
		AkkaCluster.getNodeName(null);
	}
	
	@Test
	public void getNodeName_ok() {
		String test = AkkaCluster.getNodeName("test");
		Assert.assertEquals("/user/test", test);
		
		test = AkkaCluster.getNodeName("test", "bla");
		Assert.assertEquals("/user/test/bla", test);
	}

}
