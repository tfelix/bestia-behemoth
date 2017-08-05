package net.bestia.messages.web;

import java.io.Serializable;

/**
 * Sends the user information about the status if his account registration.
 * 
 * @author Thomas Felix
 *
 */
public final class AccountRegistrationReply implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public final AccountRegistrationError error;
	
	
	public AccountRegistrationReply(AccountRegistrationError error) {
		
		this.error = error;
	}
	
	public AccountRegistrationError getError() {
		return error;
	}
}
