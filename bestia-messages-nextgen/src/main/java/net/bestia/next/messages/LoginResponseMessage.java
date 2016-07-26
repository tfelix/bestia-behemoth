package net.bestia.next.messages;

import java.util.Objects;

public class LoginResponseMessage {

	private LoginState response;
	private LoginRequestMessage requestMessage;

	public LoginResponseMessage() {

	}

	public LoginResponseMessage(LoginRequestMessage requestMsg, LoginState state) {
		this.requestMessage = Objects.requireNonNull(requestMsg, "RequestMsg can not be null.");
		this.response = state;
	}

	public LoginRequestMessage getRequestMessage() {
		return requestMessage;
	}

	public LoginState getResponse() {
		return response;
	}

}
