package net.bestia.loginserver.authenticator;

import net.bestia.model.ServiceLocator;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;

/**
 * Authenticates a given account identified by a username (email) and a password.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PasswordAuthenticator implements Authenticator {

	private final AccountDAO accountDao;

	private final String username;
	private final String password;
	
	private Account foundAccount;

	public PasswordAuthenticator(String username, String password) {
		this.username = username;
		this.password = password;

		ServiceLocator locator = ServiceLocator.getInstance();
		this.accountDao = locator.getBean(AccountDAO.class);
	}
	
	public Account getFoundAccount() {
		return foundAccount;
	}

	@Override
	public AuthState authenticate() {

		// Get the account from the database.
		final Account account = accountDao.findByEmail(username);

		// Validate the password.
		if (account == null) {
			return AuthState.NO_ACCOUNT;
		} else {
			if (account.getPassword().matches(password)) {
				
				foundAccount = account;
				
				return AuthState.AUTHENTICATED;
			} else {
				return AuthState.DENIED;
			}
		}
	}
}
