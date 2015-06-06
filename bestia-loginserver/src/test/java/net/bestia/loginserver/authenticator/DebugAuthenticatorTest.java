package net.bestia.loginserver.authenticator;

import org.junit.Test;
import static org.junit.Assert.*;

public class DebugAuthenticatorTest {

	@Test
	public void auth_test() {
		DebugAuthenticator auth = new DebugAuthenticator();
		assertEquals(AuthState.AUTHENTICATED, auth.authenticate()); 
	}
}
