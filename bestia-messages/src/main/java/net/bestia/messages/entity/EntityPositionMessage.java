package net.bestia.messages.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;

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
public class EntityPositionMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.position";

	@JsonProperty("cx")
	private long currentX;

	@JsonProperty("cy")
	private long currentY;

	public EntityPositionMessage() {
		// no op.
	}

	/**
	 * Sets the current position for the given bestia entity id.
	 * 
	 * @param entityId
	 *            The entity id to set the postion.
	 * @param x
	 *            The x position.
	 * @param y
	 *            The y postion.
	 */
	public EntityPositionMessage(long entityId, long x, long y) {
		if (x < 0 || y < 0) {
			throw new IllegalArgumentException("X and Y can not be smaller then 0.");
		}

		if (entityId < 0) {
			throw new IllegalArgumentException("EntityID must be positive.");
		}

		this.currentX = x;
		this.currentY = y;
		this.setEntityId(entityId);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("EntityPositionMessage[eid: %d, accId: %d, curX: %d, curY: %d]",
				getEntityId(), getAccountId(), currentX, currentY);
	}
}
