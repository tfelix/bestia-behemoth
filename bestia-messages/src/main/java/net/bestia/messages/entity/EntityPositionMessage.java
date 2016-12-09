package net.bestia.messages.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

/**
 * This message is purely for position changes. It will be send to the client
 * and the client will check the entity position agains the position given
 * inside this message. If they deviate too much a hard set to the coordinates
 * will be performed. Otherwise only a validation or a little adjustment will
 * happen by the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityPositionMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.position";

	private long x;
	private long y;

	@JsonProperty("eid")
	private long entityId;

	public EntityPositionMessage() {

	}

	public EntityPositionMessage(long entityId, long x, long y) {
		this.setX(x);
		this.setY(y);
		this.setEntityId(entityId);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	public long getEntityId() {
		return entityId;
	}

	public void setEntityId(long entityId) {
		this.entityId = entityId;
	}

	public long getY() {
		return y;
	}

	public void setY(long y) {
		this.y = y;
	}

	public long getX() {
		return x;
	}

	public void setX(long x) {
		this.x = x;
	}

	@Override
	public String toString() {
		return String.format("EntityPositionMessage[uuid: %s, accId: %d, x: %d, y: %d]", entityId, getAccountId(), x,
				y);
	}
}
