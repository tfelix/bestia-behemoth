package net.bestia.messages;

public class LoginReplyMessage extends LoginMessage {

	public enum LoginState {
		DENIED,
		AUTHORIZED
	}
	
	public static final String MESSAGE_PATH = "login";
	public static final String MESSAGE_ID = "system.login";
	private static final long serialVersionUID = 1L;
	
	private LoginState state;
	
	public LoginReplyMessage(LoginMessage loginMsg) {
		super(loginMsg.getToken(), loginMsg.getRequestId());
		this.setAccountId(loginMsg.getAccountId());
		
		// no op.
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
		return getAccountMessagePath();
	}


}
