package net.bestia;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import net.bestia.core.BestiaServer;
import net.bestia.core.connection.BestiaConnectionManager;
import net.bestia.core.game.service.ServiceFactory;

public class BestiaServerTest {
	
	@Mock
	BestiaConnectionManager connection;
	@Mock
	ServiceFactory servFac;
	
	String configFile = this.getClass().getClassLoader()
            .getResource("bestia.properties").toString();
	
	@Before
	protected void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);
	}
	
	@Test
	public void test_Operation() throws Exception {
		
		BestiaServer server = new BestiaServer(servFac, connection, configFile);
		server.start();
		server.stop();
	}
}
