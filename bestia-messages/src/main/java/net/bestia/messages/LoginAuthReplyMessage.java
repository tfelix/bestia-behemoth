package net.bestia.messages;

public class LoginAuthReplyMessage extends LoginAuthMessage {

	public enum LoginState {
		DENIED, AUTHORIZED
	}

	/**
	 * This messages are subjected to ALL webserver since we dont know from who the request originated. But the messages
	 * are tagged with a unique request ID so we are safe here.
	 */
	private static final String MESSAGE_PATH = "web/all";
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
	public String getMessagePath() {
		return MESSAGE_PATH;
	}
	
	@Override
	public String toString() {
		return String.format("LoginAuthReplyMessage[accountId: %d, messageId: %s, path: %s, reqId: %s, state: %s]", getAccountId(),
				getMessageId(), getMessagePath(), getRequestId(), state.toString());
	}

}
