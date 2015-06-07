package net.bestia.model.domain;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.model.domain.Password;

/**
 * Test for the Password model.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PasswordTest {
	
	private final String password = "test123";
	private final String otherPassword = "RO>All";

	@Test
	public void test_StringEquals() {
		Password pw = new Password(password);
		Assert.assertTrue(pw.matches(password));
	}
	
	@Test
	public void test_PasswordEquals() {
		Password pw = new Password(password);
		Password pw1 = new Password(password);
		Assert.assertTrue(pw.equals(pw1));
	}
	
	@Test
	public void test_StringNotEquals() {
		Password pw = new Password(password);
		Assert.assertFalse(pw.matches(otherPassword));
	}
	
	@Test
	public void test_PasswordNotEquals() {
		Password pw = new Password(password);
		Password pw1 = new Password(otherPassword);
		Assert.assertFalse(pw.equals(pw1));
	}
	
}
