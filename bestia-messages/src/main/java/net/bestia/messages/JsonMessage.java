package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * This base class is meant to provide custom serializing abilities to massages
 * for the clients. All messages meant to be send to the client via jackson
 * serialization and deserialization should use this base class.
 * 
 * @author Thomas Felix
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class JsonMessage extends AccountMessage implements MessageId {

	private static final long serialVersionUID = 1L;

	public JsonMessage(long accId) {
		super(accId);
	}

	/**
	 * Redefine the abstract class. We need to instance json messages now.
	 * Actually we want to create always the same type of message so the API
	 * stays the same. It will like
	 * {@link AccountMessage#createNewInstance(long)} create a new copy with a
	 * new account id.
	 * 
	 * @return A new copy of the immutable message pointing to a new account id.
	 */
	public abstract JsonMessage createNewInstance(long accountId);

}
