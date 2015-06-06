package net.bestia.loginserver.authenticator;

import net.bestia.loginserver.Loginserver;

/**
 * Classes implementing this interface are used to authenticate the user by which means whatsoever to the bestia
 * loginserver.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Authenticator {

	public AuthState authenticate(Loginserver server);
}
