package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * These messages carry an account information. Usually they are coming from a
 * client and are send towards a server.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class AccountMessage extends Message {

	private static final long serialVersionUID = 1L;

	private static final String MSG_PATH_ACCOUNT = "zone/account/%d";
	private static final String MSG_PATH_CLIENT = "client/%d";

	private long accountId;

	/**
	 * Std. Ctor. Note: This is here for deserialization purpose. Please specify
	 * an account id if this ctor is used.
	 */
	public AccountMessage() {
		// no op.
	}

	/**
	 * Creates a message out of a previous message. Informations like the
	 * account id and the uuid for connection identification are reused.
	 * 
	 * @param msg
	 */
	public AccountMessage(AccountMessage msg) {
		if (msg == null) {
			throw new IllegalArgumentException("Msg can not be null.");
		}

		this.accountId = msg.getAccountId();
	}

	/**
	 * Ctor. The broadcast flag can be set to send this message to all connected
	 * players.
	 * 
	 * @param isBroadcast
	 */
	public AccountMessage(long accountId) {
		this.accountId = accountId;
	}

	/**
	 * Returns the account id for this message. Note: Not everytime this id is
	 * set. If the user is not logged in this might not reflect the true id of
	 * the connected account until he has authenticated.
	 * 
	 * @return
	 */
	@JsonIgnore
	public long getAccountId() {
		return accountId;
	}

	public void setAccountId(long accountId) {
		this.accountId = accountId;
	}

	@Override
	public String toString() {
		return String.format("Message[message id: %s, account id: %d]", getMessageId(), accountId);
	}

	/**
	 * Helper method. Might be used as getMessagePath() implementation if the
	 * message is directed to the client which is quite often the case.
	 * 
	 * @return A message path designated to reach a user connected to a
	 *         webserver.
	 */
	public static String getClientMessagePath(long accountId) {
		return String.format(MSG_PATH_CLIENT, accountId);
	}

	/**
	 * Helper method. Might be used as getMessagePath() implementation if the
	 * message is intended to be received by the zones on which a client as
	 * spawned bestias (all zones will listen to account messages if they have a
	 * bestia alive).
	 * 
	 * @return A message path designated to reach zoneserver on which a certain
	 *         user is connected.
	 */
	public static String getZoneMessagePath(long accountId) {
		return String.format(MSG_PATH_ACCOUNT, accountId);
	}

}
