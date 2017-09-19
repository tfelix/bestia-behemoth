package net.bestia.messages.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.entity.component.TagComponent.Tag;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.geometry.Point;

/**
 * This sends a first rough update of an entity to all clients which are in
 * range. Such a message is send if the client itself moves and sees new
 * entities in visible range or if the entity itself was spawned.
 * 
 * @author Thomas Felix
 *
 */
public class EntityUpdateMessage extends EntityJsonMessage {

	public static class Builder {

		private SpriteInfo spriteInfo;
		private long x;
		private long y;
		private EntityAction action;
		private List<Tag> tags = new ArrayList<>();
		private long eid;
		private long accountId;

		public Builder(EntityUpdateMessage oldMsg) {

			this.spriteInfo = oldMsg.spriteInfo;
			this.x = oldMsg.x;
			this.y = oldMsg.y;
			this.action = oldMsg.action;
			this.tags = oldMsg.tags;
			this.accountId = oldMsg.getAccountId();
			this.eid = oldMsg.getEntityId();
		}

		public Builder() {

		}

		public void setEid(long eid) {
			this.eid = eid;
		}

		public void setAction(EntityAction action) {
			this.action = action;
		}

		public void setX(long x) {
			this.x = x;
		}

		public void setY(long y) {
			this.y = y;
		}

		public void setSpriteInfo(SpriteInfo spriteInfo) {
			this.spriteInfo = spriteInfo;
		}

		public List<Tag> getTags() {
			return tags;
		}

		public void setPosition(Point position) {
			Objects.requireNonNull(position);

			this.x = position.getX();
			this.y = position.getY();
		}

		public EntityUpdateMessage build() {
			return new EntityUpdateMessage(this);
		}
	}

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

	@JsonProperty("t")
	private List<Tag> tags;

	/**
	 * Priv. ctor for jackson.
	 */
	protected EntityUpdateMessage() {
		// no op.
	}

	/**
	 * Constructor which uses the builder pattern for immutable data.
	 * 
	 * @param builder
	 */
	public EntityUpdateMessage(Builder builder) {
		super(builder.accountId, builder.eid);

		this.spriteInfo = Objects.requireNonNull(builder.spriteInfo);
		this.x = builder.x;
		this.y = builder.y;
		this.action = Objects.requireNonNull(builder.action);
		this.tags = new ArrayList<>(Objects.requireNonNull(builder.tags));
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
		this.action = msg.action;
	}

	/**
	 * Creates a message setup with all the needed infos for the clients to
	 * remove a bestia entity from their display.
	 * 
	 * @param entityId
	 *            The entity to be removed.
	 * @param pos
	 *            The current position of the entity to be removed.
	 * @return An {@link EntityUpdateMessage} setup so the clients remove the
	 *         entity.
	 */
	public static EntityUpdateMessage getDespawnUpdate(long entityId, Point pos) {
		return new EntityUpdateMessage(0, entityId, pos.getX(), pos.getY(), SpriteInfo.empty(), EntityAction.DIE);
	}

	/**
	 * Creates an update message.
	 * 
	 * @param accId
	 *            The account id which should receive this message.
	 * @param entityId
	 * @param pos
	 * @param sprite
	 * @return
	 */
	public static EntityUpdateMessage getUpdate(long accId, long entityId, Point pos, SpriteInfo sprite) {
		return new EntityUpdateMessage(accId, entityId, pos.getX(), pos.getY(), sprite, EntityAction.UPDATE);
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