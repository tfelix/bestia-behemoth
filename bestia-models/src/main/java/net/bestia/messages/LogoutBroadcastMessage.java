package net.bestia.messages;

/**
 * Signals a logout from an account by whatsoever means. Zones can react then to this logout and deal with pending
 * entities.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LogoutBroadcastMessage extends Message {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "system.logoutbroadcast";

	public LogoutBroadcastMessage() {
		// no op.
	}

	public LogoutBroadcastMessage(Message message) {
		setAccountId(message.getAccountId());
	}

	public LogoutBroadcastMessage(long accountId) {
		setAccountId(accountId);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneBroadcastMessagePath();
	}

	@Override
	public String toString() {
		return String.format("LogoutBroadcastMessage[messageId: %s, path: %s]", getMessageId(), getMessagePath());
	}
}
