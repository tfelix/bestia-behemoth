package net.bestia.messages;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

	@JsonIgnore
	private long accountId;

	/**
	 * Std. ctor for deserialization purpose of child classes.
	 */
	public AccountMessage() {

	}

	/**
	 * Creates a message out of a previous message. Informations like the
	 * account id and the uuid for connection identification are reused.
	 * 
	 * @param msg
	 */
	public AccountMessage(AccountMessage msg) {

		Objects.requireNonNull(msg);
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
	public long getAccountId() {
		return accountId;
	}

	public void setAccountId(long accountId) {
		this.accountId = accountId;
	}

	@Override
	public String toString() {
		return String.format("AccountMessage[account id: %d]", accountId);
	}
}
