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
	private final byte[] salt = new byte[] { 0x1, 0x3, 0x3, 0x7, 0x1, 0x3, 0x3,
			0x7, 0x1, 0x3, 0x3, 0x7 };

	@Test
	public void matches_string() {
		Password pw = new Password(password);
		Assert.assertTrue("Same string does not match.", pw.matches(password));
	}

	@Test
	public void passwords_equal_with_same_salt() {
		Password pw = new Password(password);
		Password pw2 = new Password(password, pw.getSalt());
		Assert.assertTrue(pw.equals(pw2));
	}

	@Test
	public void password_not_equal_different_salt() {
		Password pw = new Password(password);
		Password pw2 = new Password(password);
		Assert.assertFalse(pw.equals(pw2));
	}

	@Test
	public void different_passwords_not_equal_same_salt() {
		Password pw = new Password(password);
		Password pw2 = new Password(otherPassword, pw.getSalt());
		Assert.assertFalse(pw.equals(pw2));
	}

	@Test
	public void check_given_salt_usage() {
		Password pw =new Password(password, salt);
		Assert.assertEquals(salt, pw.getSalt());
	}

	@Test
	public void different_password_not_equal() {
		Password pw = new Password(password);
		Assert.assertFalse(pw.matches(otherPassword));
	}

	@Test
	public void different_passwords_not_equal() {
		Password pw = new Password(password);
		Password pw1 = new Password(otherPassword);
		Assert.assertFalse(pw.equals(pw1));
	}

}
