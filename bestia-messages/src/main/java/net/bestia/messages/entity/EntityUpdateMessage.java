package net.bestia.messages.entity;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.model.domain.SpriteInfo;

public class EntityUpdateMessage extends JsonMessage implements EntityMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.update";

	@JsonProperty("s")
	private SpriteInfo spriteInfo;

	@JsonProperty("x")
	private long x;

	@JsonProperty("y")
	private long y;

	@JsonProperty("a")
	private EntityAction action;

	@JsonProperty("eid")
	private long entityId;

	public EntityUpdateMessage() {
		// no op.
	}

	public EntityUpdateMessage(long accId, long entityId, long x, long y, SpriteInfo info, EntityAction action) {
		this(accId, entityId, x, y, info);
		
		this.action = action;
	}

	public EntityUpdateMessage(long accId, long entityId, long x, long y, SpriteInfo info) {
		super(accId);
		
		this.entityId = entityId;
		this.x = x;
		this.y = y;
		this.spriteInfo = Objects.requireNonNull(info);
		this.action = EntityAction.APPEAR;
	}

	public long getX() {
		return x;
	}

	public void setX(long x) {
		this.x = x;
	}

	public long getY() {
		return y;
	}

	public void setY(long y) {
		this.y = y;
	}

	public EntityAction getAction() {
		return action;
	}

	public void setAction(EntityAction action) {
		this.action = action;
	}

	@Override
	public String toString() {
		return String.format("EntityUpdateMessage[eid: %d, x: %d, y: %d, sprite: %s, action: %s]",
				entityId, x, y,
				spriteInfo.toString(), action.toString());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public long getEntityId() {
		return entityId;
	}
}