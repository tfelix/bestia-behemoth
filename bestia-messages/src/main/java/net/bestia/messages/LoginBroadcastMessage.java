package net.bestia.messages;

public class LoginBroadcastMessage extends Message {

	private static final long serialVersionUID = 1L;
	private final static String MESSAGE_PATH = "zone/all";
	public final static String MESSAGE_ID = "system.loginbroadcast";

	public LoginBroadcastMessage() {
		setAccountId(0);
	}
	
	public LoginBroadcastMessage(long accountId) {
		setAccountId(accountId);
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
		return String.format("LoginBroadcastMessage[messageId: %s, path: %s, account_id: %d]", getMessageId(),
				getMessagePath(), getAccountId());
	}

}
