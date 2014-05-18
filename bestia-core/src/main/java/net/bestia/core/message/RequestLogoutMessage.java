package net.bestia.core.message;

public class RequestLogoutMessage extends Message {

	private static final String messageId = "req.logout";
	
	/**
	 * Ctor.
	 */
	public RequestLogoutMessage() {
		// no op.
	}

	@Override
	public String getMessageId() {
		return messageId;
	}
}
