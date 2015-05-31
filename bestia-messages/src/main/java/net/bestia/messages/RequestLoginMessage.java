package net.bestia.messages;

/**
 * This message will be issued upon a new connection to a webserver if a successfull login was detected. This message
 * will be delivered to all zoneserver. They will switch active the bestia master. And send sync messages for all active
 * bestias in the world. The zones will also subscribe to the new logged in account to further get updates from it.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class RequestLoginMessage extends Message {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "system.requestlogin";

	
	public RequestLoginMessage() {

	}
	
	/**
	 * 
	 * @param accountId
	 *            Account id of the logged in account.
	 */
	public RequestLoginMessage(int accountId) {
		this.setAccountId(accountId);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}
