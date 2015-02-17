package net.bestia.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.bestia.core.connection.BestiaConnectionInterface;
import net.bestia.core.game.service.AccountService;
import net.bestia.core.game.service.AccountServiceFactory;
import net.bestia.core.game.service.ServiceFactory;
import net.bestia.core.message.Message;
import net.bestia.core.message.PingMessage;
import net.bestia.core.util.CurrentThreadExecutorService;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class BestiaZoneserverTest {

	protected static ServiceFactory servFac;

	protected static BestiaZoneserver zone;
	protected static AccountServiceFactory accountServiceFactory;
	protected static ExecutorService worker = new CurrentThreadExecutorService();
	protected static FakeConnection connection = new FakeConnection();
	
	protected static class FakeConnection implements BestiaConnectionInterface {
		
		public List<Message> buffer = new ArrayList<Message>();

		@Override
		public void sendMessage(Message message) throws IOException {
			buffer.add(message);
		}

		@Override
		public boolean isConnected(int accountId) {
			return true;
		}

		@Override
		public void elevateConnection(String uuid, int accountId) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	@Before
	public void resetBuffer() {
		connection.buffer.clear();
	}

	@BeforeClass
	public static void setUp() {

		// Setup mocks.
		AccountService accService = mock(AccountService.class);

		accountServiceFactory = mock(AccountServiceFactory.class);
		when(accountServiceFactory.getAccount(anyInt())).thenReturn(accService);

		servFac = mock(ServiceFactory.class);
		when(servFac.getAccountServiceFactory()).thenReturn(
				accountServiceFactory);

		try {
			File configFile = new File(BestiaZoneserverTest.class.getClassLoader()
					.getResource("bestia.properties").toURI());

			zone = new BestiaZoneserver(servFac, connection,
					configFile.getAbsolutePath(), worker);
			
			zone.start();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@AfterClass
	public static void tearDown() {
		zone.stop(true);
	}

	/*
	@Test
	public void test_ReceiveCommand() throws Exception {

		Message msg = new PingMessage();

		zone.handleMessage(msg);

		verify(connection).sendMessage(any(Message.class));
	}*/

}
