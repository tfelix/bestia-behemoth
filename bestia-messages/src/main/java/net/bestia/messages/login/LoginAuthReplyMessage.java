package net.bestia.messages.login;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;

/**
 * Message is replied from the login server after a {@link LoginAuthMessage}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LoginAuthReplyMessage extends AccountMessage {

	public static final String MESSAGE_ID = "system.loginauthreply";
	private static final long serialVersionUID = 1L;

	private LoginState state;

	public LoginAuthReplyMessage() {
		setLoginState(LoginState.DENIED);
	}

	public LoginAuthReplyMessage(LoginState state) {
		this.state = state;
	}

	public void setLoginState(LoginState state) {
		this.state = state;
	}

	@JsonProperty("s")
	public LoginState getLoginState() {
		return state;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(state);
	}

	@Override
	public String toString() {
		return String.format("LoginAuthReplyMessage[state: %s]", state.toString());
	}

}
