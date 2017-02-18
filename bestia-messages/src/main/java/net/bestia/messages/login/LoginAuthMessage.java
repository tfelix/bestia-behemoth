package net.bestia.messages.login;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

/**
 * Message is send if a webserver wants to authenticate a pending connection. It
 * will send the given access token from the request to the login server which
 * must respond accordingly.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LoginAuthMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "system.loginauth";
	
	@JsonProperty("token")
	private String token;

	protected LoginAuthMessage() {
		// no op.
	}

	public LoginAuthMessage(long accountId, String token) {
		super(accountId);
		
		this.token = Objects.requireNonNull(token);
	}


	/**
	 * User provided login token which will be checked against in the database.
	 * 
	 * @return Login token.
	 */
	public String getToken() {
		return token;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("LoginAuthMessage[accountId: %d, token: %s]", getAccountId(), getToken());
	}

	@Override
	public LoginAuthMessage createNewInstance(long accountId) {
		
		return new LoginAuthMessage(accountId, token);
	}

}
