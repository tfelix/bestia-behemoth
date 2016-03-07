package net.bestia.messages;

public class LoginBroadcastMessage extends AccountMessage {

	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "system.loginbroadcast";

	private String token;

	public LoginBroadcastMessage() {
		setAccountId(0);
	}

	public LoginBroadcastMessage(long accountId, String token) {
		setAccountId(accountId);

		if (token == null || token.isEmpty()) {
			throw new IllegalArgumentException("Token can not be null or empty.");
		}

		this.token = token;
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
		return String.format("LoginBroadcastMessage[accId: %d, path: %s, token: %s]",
				getAccountId(), getMessagePath(), getToken());
	}

	/**
	 * Returns the used login token.
	 * 
	 * @return
	 */
	public String getToken() {
		return token;
	}

}
