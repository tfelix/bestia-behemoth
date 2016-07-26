package net.bestia.messages;

import java.io.Serializable;
import java.util.Objects;

public class LoginResponseMessage implements Serializable {

	private static final long serialVersionUID = 1L;
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
