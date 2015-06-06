package net.bestia.loginserver.authenticator;

/**
 * State of the authentication process.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public enum AuthState {

	/**
	 * Wrong password, error etc.
	 */
	DENIED,

	/**
	 * No Account found.
	 */
	NO_ACCOUNT,

	/**
	 * Authentication successful.
	 */
	AUTHENTICATED

}
