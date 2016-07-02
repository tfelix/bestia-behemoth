package net.bestia.loginserver.authenticator;

import net.bestia.model.ServiceLocator;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;

/**
 * Authenticates an account with an already saved token.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LoginTokenAuthenticator implements Authenticator {

	private final AccountDAO accountDao;

	private final String token;
	private final long accountId;

	/**
	 * Basic constructor for a authenticator. Uses the account id and a login token which must be set in the database to
	 * authenticate a login.
	 * 
	 * @param id
	 *            Account ID
	 * @param token
	 *            Login token.
	 */
	public LoginTokenAuthenticator(long id, String token) {
		this.token = token;
		this.accountId = id;

		ServiceLocator locator = ServiceLocator.getInstance();
		this.accountDao = locator.getBean(AccountDAO.class);
	}

	@Override
	public AuthState authenticate() {

		// Get the account from the database.
		final Account account = accountDao.findOne(accountId);

		if (account == null) {
			return AuthState.NO_ACCOUNT;
		} else {
			if (account.getLoginToken().equals(token)) {
				return AuthState.AUTHENTICATED;
			} else {
				return AuthState.DENIED;
			}
		}
	}

}
