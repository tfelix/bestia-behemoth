package net.bestia.util;

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
		Assert.assertEquals(4, bc.getIntProperty("initThreads").intValue());
	}
	
	@Test
	public void get_version() {
		BestiaConfiguration bc = new BestiaConfiguration();
		Assert.assertTrue(bc.getVersion().equals("alpha-0.2.7"));
	}
	
	@Test
	public void set_value_ok() throws IOException {
		BestiaConfiguration bc = new BestiaConfiguration();
		bc.load(configFile);
		bc.setValue("test", 1);
		Assert.assertTrue(bc.getIntProperty("test") == 1);
	}
	
	@Test
	public void get_value_nok() throws IOException {
		BestiaConfiguration bc = new BestiaConfiguration();
		bc.load(configFile);
		Assert.assertNull(bc.getIntProperty("test"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void getValue_dontExist_exception() throws IOException {
		BestiaConfiguration bc = new BestiaConfiguration();
		bc.load(configFile);
		bc.getProperty("thiskeydoesnotexist1234");
	}
}
