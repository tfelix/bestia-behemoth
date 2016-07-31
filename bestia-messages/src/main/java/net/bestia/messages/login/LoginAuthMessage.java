package net.bestia.messages.login;

import java.util.UUID;

import net.bestia.messages.AccountMessage;

/**
 * Message is send if a webserver wants to authenticate a pending connection. It
 * will send the given access token from the request to the login server which
 * must respond accordingly.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LoginAuthMessage extends AccountMessage {

	private static final long serialVersionUID = 1L;

	private static final String MESSAGE_PATH = "login";
	public static final String MESSAGE_ID = "system.loginauth";

	private String requestId;
	private String token;

	public LoginAuthMessage() {
		requestId = UUID.randomUUID().toString();
	}

	public LoginAuthMessage(long accountId, String token) {
		setAccountId(accountId);
		this.token = token;
		this.requestId = UUID.randomUUID().toString();
	}

	public LoginAuthMessage(String token, String requestId) {
		this.requestId = requestId;
		this.token = token;
	}

	/**
	 * The request id of this message.
	 * 
	 * @return Unique ID of this request.
	 */
	public String getRequestId() {
		return requestId;
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
		return String.format("LoginAuthMessage[accountId: %d, messageId: %s, path: %s, reqId: %s]", getAccountId(),
				getMessageId(), getRequestId());
	}

}
