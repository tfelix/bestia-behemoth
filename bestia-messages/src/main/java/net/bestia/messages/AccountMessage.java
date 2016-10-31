package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

import net.bestia.messages.jackson.MessageTypeIdResolver;

/**
 * These messages carry additional account information (the account id). Usually
 * they are coming from a client and are send towards a server. This account id
 * can be used to generate messages originating back to the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.PROPERTY, property = "mid")
@JsonTypeIdResolver(MessageTypeIdResolver.class)
public abstract class AccountMessage extends Message implements MessageId {

	private static final long serialVersionUID = 1L;

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
	
	/* (non-Javadoc)
	 * @see net.bestia.messages.MessageId#getMessageId()
	 */
	@Override
	@JsonTypeId
	public abstract String getMessageId();

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
		return String.format("AccountMessage[message id: %s, account id: %d]",
				getMessageId(),
				accountId);
	}
}
