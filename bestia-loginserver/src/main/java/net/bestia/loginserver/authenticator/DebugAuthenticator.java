package net.bestia.loginserver.authenticator;

/**
 * Simply authenticates always for debug purpose.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class DebugAuthenticator implements Authenticator {

	@Override
	public AuthState authenticate() {
		return AuthState.AUTHENTICATED;
	}

}
