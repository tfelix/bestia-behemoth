package net.bestia.loginserver.rest;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.messages.api.AccountLoginResponse;

public class AccountLoginResponseTest {

	@Test
	public void properties_test() {
		AccountLoginResponse alr = new AccountLoginResponse(1, "test", "test123");
		
		Assert.assertEquals(1, alr.getAccId());
		Assert.assertEquals("test", alr.getUsername());
		Assert.assertEquals("test123", alr.getToken());
	}
}
