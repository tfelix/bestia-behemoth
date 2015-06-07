package net.bestia.core;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutorService;

import net.bestia.core.util.CurrentThreadExecutorService;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.game.service.AccountService;
import net.bestia.zoneserver.game.service.AccountServiceManager;
import net.bestia.zoneserver.game.service.ServiceFactory;

import org.junit.AfterClass;
import org.junit.BeforeClass;

public class BestiaZoneserverTest {

	protected static ServiceFactory servFac;

	protected static Zoneserver zone;
	protected static AccountServiceManager accountServiceFactory;
	protected static ExecutorService worker = new CurrentThreadExecutorService();

	@BeforeClass
	public static void setUp() {

		// Setup mocks.
		AccountService accService = mock(AccountService.class);

		accountServiceFactory = mock(AccountServiceManager.class);
		when(accountServiceFactory.getAccount(anyInt())).thenReturn(accService);

		servFac = mock(ServiceFactory.class);
		when(servFac.getAccountServiceFactory()).thenReturn(accountServiceFactory);

		zone = new Zoneserver();
		zone.start();

	}

	@AfterClass
	public static void tearDown() {
		zone.stop();
	}

	/*
	 * @Test public void test_ReceiveCommand() throws Exception {
	 * 
	 * Message msg = new PingMessage();
	 * 
	 * zone.handleMessage(msg);
	 * 
	 * verify(connection).sendMessage(any(Message.class)); }
	 */

}
