package net.bestia.messages;

import java.util.UUID;

public class LoginAuthMessage extends Message {

	private static final long serialVersionUID = 1L;

	private static final String MESSAGE_PATH = "login";
	public static final String MESSAGE_ID = "system.loginauth";

	private String requestId;
	private String token;

	public LoginAuthMessage() {
		requestId = UUID.randomUUID().toString();
	}

	public LoginAuthMessage(String token) {
		requestId = UUID.randomUUID().toString();
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
	public String getMessagePath() {
		return MESSAGE_PATH;
	}

	@Override
	public String toString() {
		return String.format("LoginAuthMessage[accountId: %d, messageId: %s, path: %s, reqId: %s]", getAccountId(),
				getMessageId(), getMessagePath(), getRequestId());
	}

}
