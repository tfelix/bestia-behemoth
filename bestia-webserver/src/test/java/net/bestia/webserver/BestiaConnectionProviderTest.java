package net.bestia.webserver;

import org.junit.Test;

public class BestiaConnectionProviderTest {
	
	@Test(expected = IllegalStateException.class)
	public void wrong_creation() {
		BestiaConnectionProvider.getInstance();
	}

}
