package net.bestia.messages;

import java.util.Objects;

/**
 * This message is send pack to the client. It usually contains a serialized
 * JSON payload of the requested data.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ClientResponseMessage {

	private final String payload;
	private final long accountId;

	public ClientResponseMessage(long accId, String payload) {

		this.accountId = accId;
		this.payload = Objects.requireNonNull(payload);
	}

	/**
	 * Returns the payload of this message to be send to the client.
	 * 
	 * @return
	 */
	public String getPayload() {
		return payload;
	}

	/**
	 * Receiving account id of this message.
	 * 
	 * @return
	 */
	public long getAccountId() {
		return accountId;
	}
}
