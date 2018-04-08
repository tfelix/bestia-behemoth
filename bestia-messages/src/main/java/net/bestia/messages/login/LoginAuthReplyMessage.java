package net.bestia.messages.login;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.bestia.messages.JsonMessage;

import java.util.Objects;

/**
 * Message is replied from the login server after a {@link LoginAuthMessage}.
 * 
 * @author Thomas Felix
 *
 */
public class LoginAuthReplyMessage extends JsonMessage {

	public static final String MESSAGE_ID = "system.loginauthreply";
	private static final long serialVersionUID = 1L;

	
	private final LoginState state;

	@JsonProperty("username")
	private final String username;

	/**
	 * Needed for MessageTypeIdResolver
	 */
	private LoginAuthReplyMessage() {
		super(0);
		this.state = null;
		this.username = null;
	}

	@JsonCreator
	public LoginAuthReplyMessage(long accId, LoginState state, String username) {
		super(accId);

		this.state = Objects.requireNonNull(state);
		this.username = Objects.requireNonNull(username);
	}

  @JsonProperty("state")
	public LoginState getLoginState() {
		return state;
	}

	@JsonProperty("username")
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

	@Override
	public LoginAuthReplyMessage createNewInstance(long accountId) {
		return new LoginAuthReplyMessage(accountId, state, username);
	}

  public static LoginAuthReplyMessage accept(long accountId, String username) {
	  return new LoginAuthReplyMessage(accountId, LoginState.ACCEPTED, username);
  }

	public static LoginAuthReplyMessage denied(long accId) {
		return new LoginAuthReplyMessage(accId, LoginState.DENIED, "");
	}
}
