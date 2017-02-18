package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * These messages carry additional account information (the account id). Usually
 * they are coming from a client and are send towards a server. This account id
 * can be used to generate messages originating back to the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class AccountMessage extends Message {

	private static final long serialVersionUID = 1L;

	@JsonProperty("accId")
	private long accountId;

	/**
	 * Priv. ctor. This is needed for jackson.
	 */
	protected AccountMessage() {
		// no op.
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
	public long getAccountId() {
		return accountId;
	}

	/**
	 * Creates a new instance of the account message with a new account id. This
	 * method should be used if messages are send to different receiver
	 * accounts. By using this helper method immutable copies are created.
	 * 
	 * @param accountId
	 * @return
	 */
	public abstract AccountMessage createNewInstance(long accountId);


	@Override
	public String toString() {
		return String.format("AccountMessage[account id: %d]", accountId);
	}
}
