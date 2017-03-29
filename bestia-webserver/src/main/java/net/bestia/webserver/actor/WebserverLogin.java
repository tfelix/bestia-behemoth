package net.bestia.webserver.actor;

import net.bestia.messages.web.AccountLoginToken;
import net.bestia.webserver.exceptions.WrongCredentialsException;

public interface WebserverLogin {

	/**
	 * Generates a new login token for a given account and password combination.
	 * 
	 * @param accName
	 *            The user/account name.
	 * @param password
	 *            The password of this account.
	 * @return The newly generated {@link AccountLoginToken} containing a valid
	 *         login token.
	 * @throws WrongCredentialsException
	 *             If the provided accName or password were not found or did not
	 *             match.
	 */
	AccountLoginToken getLoginToken(String accName, String password) throws WrongCredentialsException;

}
