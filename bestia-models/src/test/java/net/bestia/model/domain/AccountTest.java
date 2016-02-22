package net.bestia.model.domain;

import org.junit.Assert;
import org.junit.Test;

public class AccountTest {

	@Test(expected=IllegalArgumentException.class)
	public void ctor_nullEmail_exception() {
		new Account(null, "test123");
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void ctor_nonConformEmail_exception() {
		new Account("tesest.de", "test123");
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void ctor_nullPassword_exception() {
		new Account("test@test.de", null);
	}
	
	@Test
	public void ctor_okArgs_success() {
		final Account a = new Account("test123@test.de", "test123");
		Assert.assertNotNull(a);
	}
}
