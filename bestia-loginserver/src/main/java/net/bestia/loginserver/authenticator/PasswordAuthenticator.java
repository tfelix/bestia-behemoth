package net.bestia.loginserver.authenticator;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;

import org.springframework.beans.factory.annotation.Autowired;

public class PasswordAuthenticator implements Authenticator {
	
	@Autowired
	private AccountDAO accountDao;
	
	private final String username;
	private final String password;
	
	public PasswordAuthenticator(String username, String password) {
		this.username = username;
		this.password = password;
	}

	@Override
	public AuthState authenticate() {
		
		// Get the account from the database. 		
		Account account = accountDao.findByEmail(username);

		if(account == null) {	
			return AuthState.NO_ACCOUNT;	
		} else {
			if(account.getPassword().matches(password)) {
				return AuthState.AUTHENTICATED;
			} else {
				return AuthState.DENIED;
			}
		}
	}
}
