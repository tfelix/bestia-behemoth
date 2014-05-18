package net.bestia.core.message;

public class LogoutMessage extends Message {

	public LogoutMessage() {
		
		//no op.
	}

	@Override
	public String getMessageId() {
		return "system.logout";
	}
}
