package net.bestia.messages;

import java.io.Serializable;

import net.bestia.messages.jackson.MessageTypeIdResolver;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

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

	private final static String MSG_PATH_SERVER_ALL = "servers";
	private final static String MSG_PATH_ZONE_ALL = "zone/all";
	private final static String MSG_PATH_NULL = "";

	public Message() {
		// no op.
	}

	/**
	 * Returns the id of this message. The same id is used on the client to trigger events which have subscribed for the
	 * arrival of this kind of messages.
	 * 
	 * @return Event name to be triggered on the client.
	 */
	@JsonTypeId
	public abstract String getMessageId();


	@Override
	public String toString() {
		return String.format("Message[message id: %s]", getMessageId());
	}

	/**
	 * Gets the designated message path e.g. "zone/all" if the messages are intended to be send to all zone servers. By
	 * overwriting this getMessagePath method the message itself can decide for which server they are intended.
	 * 
	 * @return The message path to which the message wants to be delivered.
	 */
	@JsonIgnore
	public abstract String getMessagePath();


	/**
	 * Returns a null value for the message path. This can be used for internal messages to the ECS system for example
	 * which should not take part in inter-server communication.
	 * 
	 * @return
	 */
	protected String getNullMessagePath() {
		return MSG_PATH_NULL;
	}
	
	/**
	 * The message will get delivered to all servers.
	 * @return
	 */
	protected String getServerBroadcastPath() {
		return MSG_PATH_SERVER_ALL;
	}

	/**
	 * Helper method. Returns the message path for addressing a broadcast to all zones.
	 * 
	 * @return A message path addressing a zone broadcast.
	 */
	protected String getZoneBroadcastMessagePath() {
		return MSG_PATH_ZONE_ALL;
	}
}
