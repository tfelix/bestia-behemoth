package net.bestia.zoneserver.messaging;

import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import net.bestia.interserver.InterserverSubscriber;

public class AccountRegistryTest {

	@Test(expected=IllegalArgumentException.class)
	public void ctor_nullSubscriber_throw() {
		new AccountRegistry(null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void registerLogin_noToken_exception() {
		AccountRegistry ar = new AccountRegistry(getMockServer());
		ar.registerLogin(1, null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void registerLogin_emptyToken_exception() {
		AccountRegistry ar = new AccountRegistry(getMockServer());
		ar.registerLogin(1, "");
	}

	@Test
	public void registerLogin_ok_subscribed() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.registerLogin(1, UUID.randomUUID().toString());
		
		Mockito.verify(sub).subscribe("zone/account/1");
	}

	@Test
	public void unregisteredLogin_ok_unsubscribed() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		String uuid = UUID.randomUUID().toString();
		ar.registerLogin(1, uuid);
		ar.unregisterLogin(1);
		
		Mockito.verify(sub).subscribe("zone/account/1");
		Mockito.verify(sub).unsubscribe("zone/account/1");
	}

	@Test
	public void unregisterLogin_notExisting_nothing() {
		AccountRegistry ar = new AccountRegistry(getMockServer());
		ar.unregisterLogin(14587);
	}

	@Test
	public void hasLogin_notExisting_false() {
		AccountRegistry ar = new AccountRegistry(getMockServer());
		Assert.assertFalse(ar.hasLogin(123));
	}

	@Test
	public void hasLogin_existingId_true() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.registerLogin(1, UUID.randomUUID().toString());
		Assert.assertTrue(ar.hasLogin(1));
	}
	
	@Test
	public void hasLogin_existingAndRightToken_true() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		String uuid = UUID.randomUUID().toString();
		ar.registerLogin(1, uuid);
		Assert.assertTrue(ar.hasLogin(1, uuid));
	}
	
	@Test
	public void hasLogin_existingAndWrongToken_false() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.registerLogin(1,  UUID.randomUUID().toString());
		Assert.assertFalse(ar.hasLogin(1,  UUID.randomUUID().toString()));
	}

	@Test(expected=IllegalArgumentException.class)
	public void setActiveBestia_nonExistingAcc_throw() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.setActiveBestia(123, 12);
	}

	@Test
	public void setActiveBestia_existingAcc_ok() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.registerLogin(1,  UUID.randomUUID().toString());
		ar.setActiveBestia(1, 12);
		Assert.assertEquals(12, ar.getActiveBestia(1));
	}
	
	@Test
	public void unsetActiveBestia_nonExistingAcc_throw() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.unsetActiveBestia(1337, 10);
	}

	@Test
	public void unsetActiveBestia_existingAcc_ok() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.registerLogin(1,  UUID.randomUUID().toString());
		ar.setActiveBestia(1, 12);
		Assert.assertEquals(12, ar.getActiveBestia(1));	
		ar.unsetActiveBestia(1, 12);
		Assert.assertEquals(0, ar.getActiveBestia(1));		
	}

	@Test
	public void unsetActiveBestia_existingAccNotExistingBestia_ok() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.registerLogin(1,  UUID.randomUUID().toString());
		Assert.assertEquals(1, ar.countOnlineUsers());
		Assert.assertEquals(0, ar.countOnlineBestias());
		ar.unsetActiveBestia(1, 123);
		Assert.assertEquals(0, ar.countOnlineBestias());
	}
	
	@Test
	public void decrementBestias_AlsoActiveBestia_1ActiveRemains() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.registerLogin(1,  UUID.randomUUID().toString());
		ar.incrementBestiaOnline(1L);
		ar.setActiveBestia(1L, 2);
		Assert.assertEquals(2, ar.countOnlineBestias());
		Assert.assertEquals(1, ar.countOnlineUsers());
		
		ar.decrementBestiaOnline(1L);
		ar.decrementBestiaOnline(1L);
		// Active bestia still remains.
		Assert.assertEquals(1, ar.countOnlineBestias());
	}

	@Test
	public void countOnlineUsers_numberOfAccounts() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.registerLogin(1,  UUID.randomUUID().toString());
		ar.setActiveBestia(1, 12);
		int i = ar.countOnlineUsers();
		Assert.assertEquals(1, i);
	}

	@Test
	public void countOnlineBestias_registerActiveRaisesOnlineBestias() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.registerLogin(1,  UUID.randomUUID().toString());
		ar.setActiveBestia(1, 12);
		ar.registerLogin(2,  UUID.randomUUID().toString());
		ar.setActiveBestia(2, 42);
		int i = ar.countOnlineBestias();
		Assert.assertEquals(2, i);
	}

	@Test
	public void incrementBestiaOnline_existingAcc_ok() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.registerLogin(1,  UUID.randomUUID().toString());
		ar.incrementBestiaOnline(1L);
		Assert.assertEquals(1, ar.countOnlineBestias());
	}

	@Test
	public void incrementBestiaOnline_nonExistingAcc_noAction() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.incrementBestiaOnline(1L);
		Assert.assertEquals(0, ar.countOnlineBestias());
	}

	@Test
	public void decrementBestiaOnline_existingAcc_ok() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.incrementBestiaOnline(1L);
		ar.decrementBestiaOnline(1L);
	}

	@Test
	public void decrementBestiaOnline_nonExistingAcc_throw() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		Assert.assertEquals(0, ar.countOnlineBestias());
		ar.decrementBestiaOnline(2L);
		Assert.assertEquals(0, ar.countOnlineBestias());
	}

	@Test
	public void getActiveBestia_existingAccActiveBestia_ok() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		ar.registerLogin(1,  UUID.randomUUID().toString());
		ar.setActiveBestia(1L, 1337);
		Assert.assertEquals(1337, ar.getActiveBestia(1L));
	}

	@Test
	public void getActiveBestia_existingAccNoneActiveBestia_0() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		Assert.assertEquals(0, ar.getActiveBestia(1L));
	}
	
	@Test
	public void getActiveBestia_notExistingAcc_0() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		Assert.assertEquals(0, ar.getActiveBestia(1L));
	}
	
	private InterserverSubscriber getMockServer() {
		final InterserverSubscriber subscriber = Mockito.mock(InterserverSubscriber.class);
		return subscriber;
	}

}
