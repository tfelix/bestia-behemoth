package net.bestia.messages;

import java.io.Serializable;

import net.bestia.messages.jackson.MessageTypeIdResolver;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
	private long accountId;
	private boolean isBroadcast = false;
	@JsonProperty("pbid")
	private int playerBestiaId = 0;

	private final static String MSG_PATH_ZONE_ALL = "zone/all";

	public Message() {
		// no op.
	}

	/**
	 * Creates a message out of a previous message. Informations like the account id and the uuid for connection
	 * identification are reused.
	 * 
	 * @param msg
	 */
	public Message(Message msg) {
		this.accountId = msg.getAccountId();
	}

	/**
	 * Ctor. The broadcast flag can be set to send this message to all connected players.
	 * 
	 * @param isBroadcast
	 */
	public Message(int accountId, boolean isBroadcast) {
		this.accountId = accountId;
		this.isBroadcast = isBroadcast;
	}

	@JsonIgnore
	public boolean isBroadcast() {
		return isBroadcast;
	}

	/**
	 * Returns the id of this message. The same id is used on the client to trigger events which have subscribed for the
	 * arrival of this kind of messages.
	 * 
	 * @return Event name to be triggered on the client.
	 */
	@JsonTypeId
	public abstract String getMessageId();

	/**
	 * Returns the account id for this message. Note: Not everytime this id is set. If the user is not logged in this
	 * might not reflect the true id of the connected account until he has authenticated.
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

	/**
	 * Gets the player bestia id for which this message is meant. Not all messages are subject to a selected player
	 * bestia thus this field can contain the ID 0 which is reserved for this case.
	 * TODO Das runter ziehen in die Child class.
	 * @return
	 */
	@JsonIgnore
	public int getPlayerBestiaId() {
		return playerBestiaId;
	}
	
	// TODO das in die InputMessage Child class ziehen.
	public void setPlayerBestiaId(int playerBestiaId) {
		this.playerBestiaId = playerBestiaId;
	}

	@Override
	public String toString() {
		return String.format("Message[message id: %s, account id: %d]", getMessageId(), accountId);
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
	 * Helper method. Might be used as getMessagePath() implementation if the message is directed to the client which is
	 * quite often the case.
	 * 
	 * @return A message path designated to reach a user connected to a webserver.
	 */
	protected String getClientMessagePath() {
		return String.format("account/%d", getAccountId());
	}

	/**
	 * Helper method. Might be used as getMessagePath() implementation if the message is intended to be received by the
	 * zone server on which a client originating this message is connected.
	 * 
	 * @return A message path designated to reach zoneserver on which a certain user is connected.
	 */
	protected String getZoneMessagePath() {
		return String.format("zone/account/%d", getAccountId());
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
