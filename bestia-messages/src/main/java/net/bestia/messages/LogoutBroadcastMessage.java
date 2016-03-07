package net.bestia.messages;

/**
 * Signals a logout from an account by whatsoever means. Zones can react then to
 * this logout and deal with pending entities.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LogoutBroadcastMessage extends AccountMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "system.logoutbroadcast";

	private final String token;

	public LogoutBroadcastMessage() {
		this.token = null;
	}

	public LogoutBroadcastMessage(AccountMessage message) {
		setAccountId(message.getAccountId());

		token = null;
	}

	public LogoutBroadcastMessage(long accountId) {
		setAccountId(accountId);

		token = null;
	}

	/**
	 * This {@link LogoutBroadcastMessage} is only valid for the account with
	 * the given login token.
	 * 
	 * @param accountId
	 * @param token
	 */
	public LogoutBroadcastMessage(long accountId, String token) {
		setAccountId(accountId);

		this.token = token;
	}

	public String getToken() {
		return token;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getServerBroadcastPath();
	}

	@Override
	public String toString() {
		return String.format("LogoutBroadcastMessage[accId: %d, path: %s, token: %s]", getAccountId(),
				getMessagePath(), getToken());
	}
}
