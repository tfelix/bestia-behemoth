package net.bestia.messages.login;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.MessageId;

/**
 * Message is replied from the login server after a {@link LoginAuthMessage}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LoginAuthReplyMessage extends AccountMessage implements MessageId {

	public static final String MESSAGE_ID = "system.loginauthreply";
	private static final long serialVersionUID = 1L;

	@JsonProperty("state")
	private LoginState state;
	
	@JsonProperty("username")
	private String username;

	public LoginAuthReplyMessage() {
		setLoginState(LoginState.DENIED);
	}
	
	public LoginAuthReplyMessage(long accId, LoginState state, String username) {
		setAccountId(accId);
		this.state = state;
		this.username = Objects.requireNonNull(username);
	}

	public LoginAuthReplyMessage(LoginState state, String username) {
		this(0, state, username);
	}

	public void setLoginState(LoginState state) {
		this.state = state;
	}

	public LoginState getLoginState() {
		return state;
	}
	
	public String getUsername() {
		return username;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format(
				"LoginAuthReplyMessage[accountId: %d, state: %s]", 
				getAccountId(), 
				state.toString());
	}

}
