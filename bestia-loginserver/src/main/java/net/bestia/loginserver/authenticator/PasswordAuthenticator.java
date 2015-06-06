package net.bestia.loginserver.authenticator;

import java.io.IOException;

import net.bestia.loginserver.Loginserver;
import net.bestia.messages.LoginMessage;
import net.bestia.messages.LoginReplyMessage;
import net.bestia.messages.LoginReplyMessage.LoginState;
import net.bestia.model.Account;

public class PasswordAuthenticator implements Authenticator {
	
	private final String username;
	private final String password;
	
	public PasswordAuthenticator(String username, String password) {
		this.username = username;
		this.password = password;
	}

	@Override
	public AuthState authenticate(Loginserver server) {
		
		// Get the account from the database. 
		// TODO
		loginMsg.getAccountId();
		
		Acc
		
		Account account = null;
		LoginReplyMessage loginReplyMsg = new LoginReplyMessage(loginMsg);
		if(account.getLoginToken().equals(loginMsg.getToken())) {
			loginReplyMsg.setLoginState(LoginState.AUTHORIZED);
		} else {
			loginReplyMsg.setLoginState(LoginState.DENIED);
		}
		
		try {
			publisher.publish(loginReplyMsg);
		} catch (IOException e) {
			log.error("Could not send LoginReplyMessage", e);
		}
		
		
	}

}
