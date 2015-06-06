package net.bestia.messages;

import java.util.UUID;

public class LoginMessage extends Message {

	private static final long serialVersionUID = 1L;
	
	public static final String MESSAGE_PATH = "login";
	public static final String MESSAGE_ID = "system.login";
	
	private UUID requestId;
	private String token;
	
	public LoginMessage() {
		requestId = UUID.randomUUID();
	}
	
	public LoginMessage(String token) {
		requestId = UUID.randomUUID();
	}
	
	public LoginMessage(String token, UUID requestId) {
		this.requestId = requestId;
		this.token = token;
	}
	
	/**
	 * The request id of this message.
	 * 
	 * @return
	 */
	public UUID getRequestId() {
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
