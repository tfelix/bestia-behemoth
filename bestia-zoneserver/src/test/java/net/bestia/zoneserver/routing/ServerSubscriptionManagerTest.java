package net.bestia.zoneserver.routing;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;

import net.bestia.zoneserver.Zoneserver;

public class ServerSubscriptionManagerTest {

	@Test
	public void countUser_ok() {
		ServerSubscriptionManager ssm = new ServerSubscriptionManager(getServerMock());

		ssm.setOnline(1L);
		ssm.setOnline(1L);
		ssm.setOnline(2L);

		Assert.assertEquals(2, ssm.countUser());
		ssm.setOffline(2L);
		Assert.assertEquals(1, ssm.countUser());
		ssm.setOffline(1L);
		Assert.assertEquals(1, ssm.countUser());
		ssm.setOffline(1L);
		Assert.assertEquals(0, ssm.countUser());
	}

	@Test
	public void setOffline_nok() {
		ServerSubscriptionManager ssm = new ServerSubscriptionManager(getServerMock());
		
		ssm.setOffline(5L);
		
		Assert.assertEquals(0, ssm.countUser());
		Assert.assertEquals(0, ssm.countBestias());
	}
	
	@Test
	public void setOnline_callback_ok() {
		final Zoneserver server = getServerMock();
		ServerSubscriptionManager ssm = new ServerSubscriptionManager(server);
		
		ssm.setOnline(5L);
		
		verify(server).subscribe(Matchers.matches("zone/account/5"));
	}
	
	@Test
	public void setOffline_callback_ok() {
		final Zoneserver server = getServerMock();
		ServerSubscriptionManager ssm = new ServerSubscriptionManager(server);
		
		ssm.setOnline(5L);
		ssm.setOffline(5L);
		
		verify(server).unsubscribe(Matchers.matches("zone/account/5"));
	}

	@Test
	public void countBestias_ok() {
		ServerSubscriptionManager ssm = new ServerSubscriptionManager(getServerMock());

		ssm.setOnline(1L);
		ssm.setOnline(1L);
		ssm.setOnline(2L);

		Assert.assertEquals(3, ssm.countBestias());
		ssm.setOffline(2L);
		Assert.assertEquals(2, ssm.countBestias());
		ssm.setOffline(1L);
		Assert.assertEquals(1, ssm.countBestias());
		ssm.setOffline(1L);
		Assert.assertEquals(0, ssm.countBestias());
	}

	public Zoneserver getServerMock() {
		final Zoneserver server = Mockito.mock(Zoneserver.class);
		return server;
	}

}
