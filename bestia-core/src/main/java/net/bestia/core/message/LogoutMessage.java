package net.bestia.core.message;

public class LogoutMessage extends Message {
	
	public static final String MESSAGE_ID = "system.logout";

	public LogoutMessage() {
		// no op.
	}
	
	public LogoutMessage(Message message) {
		setAccountId(message.getAccountId());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}
