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

	@JsonProperty("cx")
	private long currentX;

	@JsonProperty("cy")
	private long currentY;

	@JsonProperty("nx")
	private long nextX;

	@JsonProperty("ny")
	private long nextY;

	/**
	 * Walkspeed is in integern because we can only round floats too bad.
	 */
	@JsonProperty("s")
	private int speed;

	@JsonProperty("eid")
	private long entityId;

	public EntityPositionMessage() {

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
	public EntityPositionMessage(long entityId, long x, long y, long nextX, long nextY, int speed) {
		this(entityId, x, y);

		if (nextX < 0 || nextY < 0) {
			throw new IllegalArgumentException("nextX and nextY must be bigger then 0.");
		}

		if (speed < 0 || speed > 200) {
			throw new IllegalArgumentException("Speed must be between 0 and 200 (inclusive).");
		}

		this.nextX = nextX;
		this.nextY = nextY;
		this.speed = speed;
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

	@Override
	public String toString() {
		return String.format("EntityPositionMessage[eid: %s, accId: %d, curX: %d, curY: %d, nextX: %d, nextY: %d]",
				entityId, getAccountId(), currentX, currentY);
	}
}
