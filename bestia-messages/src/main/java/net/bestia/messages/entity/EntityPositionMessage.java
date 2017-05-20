package net.bestia.messages.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.model.geometry.Point;

/**
 * This message is purely for position changes. It will be send to the client
 * and the client will check the entity position against the position given
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

	@JsonProperty("x")
	private long currentX;

	@JsonProperty("y")
	private long currentY;

	public EntityPositionMessage() {
		// no op.
	}

	/**
	 * Sets the current receiver account to 0. This is useful if the receiver
	 * account is not yet known upon creation of this message.
	 * 
	 * @param entityId
	 *            Entity triggering this message.
	 * @param x
	 *            X Position of the entity.
	 * @param y
	 *            Y Position of the entity.
	 */
	public EntityPositionMessage(long entityId, long x, long y) {
		this(0, entityId, x, y);
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
	public EntityPositionMessage(long accId, long entityId, long x, long y) {
		super(accId, entityId);

		if (x < 0 || y < 0) {
			throw new IllegalArgumentException("X and Y can not be smaller then 0.");
		}

		this.currentX = x;
		this.currentY = y;
	}

	public EntityPositionMessage(long entityId, Point newPos) {
		this(0, entityId, newPos.getX(), newPos.getY());
		// no op.
	}

	@JsonIgnore
	public long getX() {
		return currentX;
	}

	@JsonIgnore
	public long getY() {
		return currentX;
	}

	@JsonIgnore
	public Point getPosition() {
		return new Point(currentX, currentY);
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

	@Override
	public EntityPositionMessage createNewInstance(long accountId) {
		return new EntityPositionMessage(accountId, getEntityId(), currentX, currentY);
	}
}
