package net.bestia.messages.entity;

import net.bestia.messages.AccountMessage;

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
public class EntityPositionMessage extends AccountMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.posupdate";

	private int x;
	private int y;
	private String entityId;

	public EntityPositionMessage() {

	}

	public EntityPositionMessage(String entityId, long accId, int pbid, int x, int y) {
		this.setX(x);
		this.setY(y);
		this.setEntityId(entityId);

		setAccountId(accId);
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
		return String.format("EntityUpdateMessage[uuid: %s, accId: %d, x: %d, y: %d]", entityId, getAccountId(), x, y);
	}
}
