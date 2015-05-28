package net.bestia.conf;

import java.io.File;
import java.io.IOException;

import net.bestia.util.BestiaConfiguration;

import org.junit.Assert;
import org.junit.Test;


public class BestiaConfigurationTest {
	
	private static final File configFile;
	static {
		 ClassLoader classLoader = BestiaConfigurationTest.class.getClassLoader();
		 configFile = new File(classLoader.getResource("bestia2.properties").getFile());
	}

	@Test
	public void load_default_test() throws IOException {
		BestiaConfiguration bc = new BestiaConfiguration();
		bc.load();
	}
	
	@Test
	public void load_file_test() throws IOException {			
		BestiaConfiguration bc = new BestiaConfiguration();
		bc.load(configFile);
		
		Assert.assertEquals("test123" ,bc.getProperty("fileName"));
	}
	
	@Test
	public void is_loaded_test() throws IOException {
		BestiaConfiguration bc = new BestiaConfiguration();
		Assert.assertFalse(bc.isLoaded());
		bc.load();
		Assert.assertTrue(bc.isLoaded());
	}
	
	@Test
	public void get_domain_port_string_test() throws IOException {
		BestiaConfiguration bc = new BestiaConfiguration();
		bc.load(configFile);
		Assert.assertEquals("tcp://localhost:1234", bc.getDomainPortString("domain", "port", "tcp://"));
	}
	
	@Test
	public void get_int_property() throws IOException {
		BestiaConfiguration bc = new BestiaConfiguration();
		bc.load(configFile);
		Assert.assertEquals(4, bc.getIntProperty("initThreads"));
	}
	
}
