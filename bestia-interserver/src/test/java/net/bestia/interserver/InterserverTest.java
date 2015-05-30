package net.bestia.interserver;

import net.bestia.util.BestiaConfiguration;

import org.junit.Test;

public class InterserverTest {
	
	@Test
	public void start_stop_test() throws Exception {
		BestiaConfiguration config = new BestiaConfiguration();
		config.load();
		Interserver server = new Interserver(config);
		server.start();
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void not_loaded_config_test() {
		BestiaConfiguration config = new BestiaConfiguration();
		Interserver server = new Interserver(config);
		server.start();
	}
	
}
