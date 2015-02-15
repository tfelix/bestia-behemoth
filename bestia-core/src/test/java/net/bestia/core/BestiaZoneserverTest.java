package net.bestia.core;

import java.io.File;
import java.util.concurrent.ExecutorService;

import net.bestia.core.connection.BestiaConnectionInterface;
import net.bestia.core.game.service.AccountService;
import net.bestia.core.game.service.AccountServiceFactory;
import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.message.Message;
import net.bestia.core.message.PingMessage;
import net.bestia.core.util.CurrentThreadExecutorService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class BestiaZoneserverTest {

	BestiaConnectionInterface connection;
	ServiceFactory servFac;

	BestiaZoneserver zone;
	AccountServiceFactory accountServiceFactory;
	ExecutorService worker = new CurrentThreadExecutorService();

	@Before
	public void setUp() throws Exception {

		// Setup mocks.
		AccountService accService = mock(AccountService.class);

		accountServiceFactory = mock(AccountServiceFactory.class);
		when(accountServiceFactory.getAccount(anyInt())).thenReturn(accService);

		servFac = mock(ServiceFactory.class);
		when(servFac.getAccountServiceFactory()).thenReturn(
				accountServiceFactory);

		connection = mock(BestiaConnectionInterface.class);
		when(connection.isConnected(anyInt())).thenReturn(true);

		File configFile = new File(this.getClass().getClassLoader()
				.getResource("bestia.properties").toURI());

		zone = new BestiaZoneserver(servFac, connection,
				configFile.getAbsolutePath(), worker);
		zone.start();
	}

	@After
	public void tearDown() {
		zone.stop(true);
	}

	@Test
	public void test_ReceiveCommand() throws Exception {

		Message msg = new PingMessage();

		zone.handleMessage(msg);

		verify(connection).sendMessage(any(Message.class));
	}

}
