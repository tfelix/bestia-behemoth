package net.bestia.messages.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;

/**
 * Message is send to the client in order to update a entity which is currently
 * moving to a certain tile. The client must update the entity and move it in a
 * certain time to the given tile coordiante.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.>
 *
 */
public class EntityMoveMessage extends AccountMessage {

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
