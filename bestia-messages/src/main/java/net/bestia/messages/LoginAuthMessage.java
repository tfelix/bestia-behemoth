package net.bestia.messages;

import java.util.UUID;

public class LoginAuthMessage extends Message {

	private static final long serialVersionUID = 1L;
	
	public static final String MESSAGE_PATH = "login";
	public static final String MESSAGE_ID = "system.login";
	
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
	 * @return
	 */
	public String getRequestId() {
		return requestId;
	}
	
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

}
