package net.bestia.core.message;

public class LogoutMessage extends Message {
	
	public static final String MESSAGE_ID = "system.logout";

	public LogoutMessage(Message message) {
		setAccountId(message.getAccountId());
		setUUID(message.getUUID());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}
