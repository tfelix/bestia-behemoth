package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

/**
 * This base class is meant to provide custom serializing abilities to massages
 * for the clients. All messages meant to be send to the client via jackson
 * serialization and deserialization should use this base class.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.PROPERTY, property = "mid")
@JsonTypeIdResolver(MessageTypeIdResolver.class)
public abstract class JsonMessage extends AccountMessage implements MessageId {

	private static final long serialVersionUID = 1L;
	
	public JsonMessage() {
		// no op.
	}
	
	public JsonMessage(AccountMessage msg) {
		super(msg);
	}

	public JsonMessage(long accId) {
		super(accId);
	}
	

}
