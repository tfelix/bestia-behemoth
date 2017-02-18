package net.bestia.messages.entity;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.model.domain.SpriteInfo;

public class EntityUpdateMessage extends EntityJsonMessage {

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

	/**
	 * Priv. ctor for jackson.
	 */
	protected EntityUpdateMessage() {
		// no op.
	}

	public EntityUpdateMessage(long accId, long entityId, long x, long y, SpriteInfo info, EntityAction action) {
		this(accId, entityId, x, y, info);
		
		this.action = action;
	}

	public EntityUpdateMessage(long accId, long entityId, long x, long y, SpriteInfo info) {
		super(accId, entityId);
		
		this.x = x;
		this.y = y;
		this.spriteInfo = Objects.requireNonNull(info);
		this.action = EntityAction.APPEAR;
	}
	
	public EntityUpdateMessage(long accId, EntityUpdateMessage msg) {
		super(accId, msg.getEntityId());
		
		this.x = msg.x;
		this.y = msg.y;
		this.spriteInfo = msg.spriteInfo;
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
				getEntityId(), x, y,
				spriteInfo.toString(), action.toString());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public EntityUpdateMessage createNewInstance(long accountId) {
		return new EntityUpdateMessage(accountId, this);
	}
}