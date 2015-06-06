package net.bestia.loginserver.authenticator;

import org.junit.Test;
import static org.junit.Assert.*;


public class LoginTokenAuthenticatorTest {

	@Test
	public void auth_test() {
		
		// TODO hier noch irgendwie die TEST DB aufsetzen.
		
		LoginTokenAuthenticator auth = new LoginTokenAuthenticator(1, "test-1234-1234");
		assertEquals(AuthState.DENIED, auth.authenticate());
		
		auth = new LoginTokenAuthenticator(10000000, "test-1234-1234");
		assertEquals(AuthState.NO_ACCOUNT, auth.authenticate());
		
		// TODO Auth state noch testen.
	}
	
}
