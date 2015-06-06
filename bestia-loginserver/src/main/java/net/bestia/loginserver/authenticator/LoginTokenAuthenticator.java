package net.bestia.loginserver.authenticator;

import net.bestia.model.Account;
import net.bestia.model.dao.AccountDAO;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * Authenticates an account with an already saved token.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LoginTokenAuthenticator implements Authenticator {

	@Autowired
	private AccountDAO accountDao;
	
	private final String token;
	private final long accountId;
	
	public LoginTokenAuthenticator(long id, String token) {
		this.token = token;
		this.accountId = id;
	}

	@Override
	public AuthState authenticate() {
		
		// Get the account from the database. 		
		Account account = accountDao.find(accountId);

		if(account == null) {	
			return AuthState.NO_ACCOUNT;	
		} else {
			if(account.getLoginToken().equals(token)) {
				return AuthState.AUTHENTICATED;
			} else {
				return AuthState.DENIED;
			}
		}
	}

}
