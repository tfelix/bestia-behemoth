package net.bestia.zoneserver.messaging;

import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;

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
		
		// TODO CHECKEN.
		Assert.fail();
	}

	@Test
	public void unregisteredLogin_ok_unsubscribed() {
		InterserverSubscriber sub = getMockServer();
		AccountRegistry ar = new AccountRegistry(sub);
		String uuid = UUID.randomUUID().toString();
		ar.registerLogin(1, uuid);
		ar.unregisterLogin(1);
		
		// TODO CHECKEN.
		Assert.fail();
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
	
	@Test(expected=IllegalArgumentException.class)
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
		ar.unsetActiveBestia(1, 12);
		
		// Bestia now offline.
		Assert.assertEquals(0, ar.getActiveBestia(1));		
	}

	public void unsetActiveBestia_existingAccNotExistingBestia_ok() {

	}

	public void countOnlineUsers_numberOfAccounts() {

	}

	public void countOnlineBestias_numberOfBestias() {

	}

	public void incrementBestiaOnline_existingAcc_ok() {

	}

	public void incrementBestiaOnline_nonExistingAcc_throw() {

	}

	public void decrementBestiaOnline_existingAcc_ok() {

	}

	public void decrementBestiaOnline_nonExistingAcc_throw() {

	}

	public void getActiveBestia_existingAccActiveBestia_ok() {

	}

	public void getActiveBestia_existingAccNoneActiveBestia_0() {
	}
	
	private InterserverSubscriber getMockServer() {
		
		return null;
	}

}
