package net.bestia.messages;

public class LogoutMessage extends Message {

	private static final long serialVersionUID = 1L;
	
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
