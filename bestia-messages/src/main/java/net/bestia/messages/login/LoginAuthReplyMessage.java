package net.bestia.messages.login;

import java.util.Objects;

/**
 * Message is replied from the login server after a {@link LoginAuthMessage}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LoginAuthReplyMessage extends LoginAuthMessage {

	/**
	 * State of the authorization.
	 */
	public enum LoginState {
		DENIED, AUTHORIZED
	}

	public static final String MESSAGE_ID = "system.loginauthreply";
	private static final long serialVersionUID = 1L;

	private LoginState state;

	public LoginAuthReplyMessage() {
		super(0, "");
		setLoginState(LoginState.DENIED);
	}

	public LoginAuthReplyMessage(LoginAuthMessage loginMsg) {
		super(loginMsg.getToken(), loginMsg.getRequestId());
		this.setAccountId(loginMsg.getAccountId());
	}

	public void setLoginState(LoginState state) {
		this.state = state;
	}

	public LoginState getLoginState() {
		return state;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(state, getAccountId(), getRequestId());
	}

	@Override
	public String toString() {
		return String.format("LoginAuthReplyMessage[accountId: %d, messageId: %s, path: %s, reqId: %s, state: %s]",
				getAccountId(), getMessageId(), getRequestId(), state.toString());
	}

}
