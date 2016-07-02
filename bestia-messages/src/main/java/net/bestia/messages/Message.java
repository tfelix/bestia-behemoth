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

	private final static String MSG_PATH_SERVER_BROADCAST = "servers";
	private final static String MSG_PATH_ZONE_BROADCAST = "zone";
	private final static String MSG_PATH_LOGIN = "login";

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
		return String.format("Message[message id: %s, path: %s]", getMessageId(), getMessagePath());
	}

	/**
	 * Gets the designated message path e.g. "zone/all" if the messages are
	 * intended to be send to all zone servers. By overwriting this
	 * getMessagePath method the message itself can decide for which server they
	 * are intended.
	 * 
	 * @return The message path to which the message wants to be delivered.
	 */
	@JsonIgnore
	public abstract String getMessagePath();

	/**
	 * The message will get delivered to all servers.
	 */
	public static String getServerBroadcastPath() {
		return MSG_PATH_SERVER_BROADCAST;
	}

	/**
	 * Helper method. Returns the message path for addressing a broadcast to all
	 * zones.
	 * 
	 */
	public static String getZoneBroadcastMessagePath() {
		return MSG_PATH_ZONE_BROADCAST;
	}

	/**
	 * Message is directed to the login server.
	 */
	public static String getLoginMessagePath() {
		return MSG_PATH_LOGIN;
	}
}
