package net.bestia.messages.entity;

import java.util.Queue;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;

/**
 * This message is send from the server to all clients as soon as a movement is
 * started. It contains the path of the bestia aswell as the speed. As long as
 * nothing changes along the path no further update is send and the client can
 * use this information to interpolate the movement of the entity. If a new
 * {@link EntityMoveMessage} is send by the server it takes precedence over the
 * old one.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.>
 *
 */
public class EntityMoveMessage extends AccountMessage {
	
	private class Cords {
		int x;
		int y;
	}

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.move";

	private int x;
	private int y;

	@JsonProperty("s")
	private int speed;

	@JsonProperty("uuid")
	private String entityId;

	public EntityMoveMessage() {

	}

	public EntityMoveMessage(String entityId, long accId, int x, int y, int speed) {
		super(accId);

		this.x = x;
		this.y = y;
		this.entityId = entityId;
		this.speed = speed;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

	public String getEntityId() {
		return entityId;
	}

	public void setEntityId(String entityId) {
		this.entityId = entityId;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	@Override
	public String toString() {
		return String.format("EntityMoveMessage[uuid: %s, accId: %d, x: %d, y: %d]", entityId, getAccountId(), x, y);
	}
}
