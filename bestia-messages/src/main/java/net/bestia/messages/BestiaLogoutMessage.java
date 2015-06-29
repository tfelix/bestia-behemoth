package net.bestia.messages;

public class BestiaLogoutMessage extends InputMessage {

	public static final String MESSAGE_ID = "bestia.logout";
	
	private static final long serialVersionUID = 1L;
	
	public BestiaLogoutMessage() {
		// TODO Auto-generated constructor stub
	}
	
	public BestiaLogoutMessage(Message msg, int pbid) {
		super(msg, pbid);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		// Is a internal message not so important.
		return getZoneMessagePath();
	}
	
	@Override
	public String toString() {
		return "BestiaLogoutMessage[]";
	}

}
