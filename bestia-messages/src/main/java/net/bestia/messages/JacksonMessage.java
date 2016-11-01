package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

import net.bestia.messages.jackson.MessageTypeIdResolver;

/**
 * This base class is meant to provide custom serializing abilities to massages
 * for the clients. All messages meant to be send to the client via jackson
 * serialization and deserialization should use this base class.
 * 
 * @author Thomas
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.PROPERTY, property = "mid")
@JsonTypeIdResolver(MessageTypeIdResolver.class)
public abstract class JacksonMessage extends AccountMessage implements MessageId {

	private static final long serialVersionUID = 1L;
	
	public JacksonMessage() {
		// no op.
	}
	
	public JacksonMessage(AccountMessage msg) {
		super(msg);
	}
	

}
