package net.bestia.messages.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.MessageId;
import net.bestia.model.entity.VisualType;

public class EntityUpdateMessage extends AccountMessage implements MessageId {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.update";

	public String uuid;
	
	@JsonProperty("s")
	public String sprite;
	
	@JsonProperty("x")
	public int x;
	
	@JsonProperty("y")
	public int y;
	
	@JsonProperty("t")
	public VisualType type;
	
	@JsonProperty("a")
	public EntityAction action;
	
	@JsonProperty("pbid")
	public Integer playerBestiaId;
	
	public EntityUpdateMessage() {
		// no op.
	}

	public EntityUpdateMessage(VisualType type, EntityAction action) {
		this.type = type;
		this.action = action;
	}

	public EntityUpdateMessage(String uuid, int x, int y) {
		this.uuid = uuid;
		this.x = x;
		this.y = y;
		this.action = EntityAction.APPEAR;
	}

	/**
	 * Returns a Entity with the action preset to APPEAR.
	 * 
	 * @param uuid
	 * @param x
	 * @param y
	 * @return Entity with action preset to appear.
	 */
	public static EntityUpdateMessage getAppearEntity(String uuid, int x, int y) {
		final EntityUpdateMessage e = new EntityUpdateMessage(uuid, x, y);
		e.setAction(EntityAction.APPEAR);
		return e;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getSprite() {
		return sprite;
	}

	public void addSprite(String sprite) {
		this.sprite = sprite;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public VisualType getType() {
		return type;
	}

	public void setType(VisualType type) {
		this.type = type;
	}

	public EntityAction getAction() {
		return action;
	}

	public void setAction(EntityAction action) {
		this.action = action;
	}

	public Integer getPlayerBestiaId() {
		return playerBestiaId;
	}

	public void setPlayerBestiaId(int pbid) {
		this.playerBestiaId = pbid;
	}

	@Override
	public String toString() {
		return String.format("EntityUpdateMessage[uuid: %s, x: %d, y: %d, sprite: %s, type: %s, action: %s]",
				uuid, x, y,
				sprite, type.toString(), action.toString());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}