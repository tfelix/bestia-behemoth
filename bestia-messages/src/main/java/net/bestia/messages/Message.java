package net.bestia.messages;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

import net.bestia.messages.jackson.MessageTypeIdResolver;

/**
 * Base for all messages in the bestia server.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.PROPERTY, property = "mid")
@JsonTypeIdResolver(MessageTypeIdResolver.class)
public abstract class Message implements Serializable {

	private static final long serialVersionUID = 2015052401L;

	public Message() {
		// no op.
	}

	/**
	 * Returns the id of this message. The same id is used on the client to
	 * trigger events which have subscribed for the arrival of this kind of
	 * messages.
	 * 
	 * @return Event name to be triggered on the client.
	 */
	@JsonTypeId
	public abstract String getMessageId();

	@Override
	public String toString() {
		return String.format("Message[message id: %s]", getMessageId());
	}
}
