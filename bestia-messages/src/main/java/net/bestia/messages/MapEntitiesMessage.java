package net.bestia.messages;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.entity.SpriteType;

/**
 * This message contains a list of (visible) entities around a player. These
 * entities consist out of a sprite, a position and additional data that should
 * be preloaded and is associated with an entity. For example existing sounds or
 * attack animations which can be triggered spontaneously. These should be
 * loaded as soon as possible from the client engine.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapEntitiesMessage extends Message {

	/**
	 * Certain actions can not be told to the client via an entity message
	 * alone. Some actions like a disappearing entity must be send via this
	 * special action marker.
	 *
	 */
	public enum EntityAction {
		APPEAR, DIE, VANISH, UPDATE
	}

	/**
	 * Describes an entity on the map for the engine to display.
	 */
	@JsonInclude(Include.ALWAYS)
	public static class Entity implements Serializable {

		private static final long serialVersionUID = 1L;

		private String uuid;
		@JsonProperty("s")
		private String sprite;
		@JsonProperty("x")
		private int x;
		@JsonProperty("y")
		private int y;
		@JsonProperty("t")
		private SpriteType type = SpriteType.NONE;
		@JsonProperty("a")
		private EntityAction action = null;
		@JsonProperty("pbid")
		private Integer playerBestiaId;

		public Entity() {

		}

		public Entity(String uuid, int x, int y) {
			this.uuid = uuid;
			this.x = x;
			this.y = y;
		}

		/**
		 * Returns a Entity with the action preset to APPEAR.
		 * 
		 * @param uuid
		 * @param x
		 * @param y
		 * @return Entity with action preset to appear.
		 */
		public static Entity getAppearEntity(String uuid, int x, int y) {
			final Entity e = new Entity(uuid, x, y);
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

		public SpriteType getType() {
			return type;
		}

		public void setType(SpriteType type) {
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
			return String.format("Entity[uuid: %s, x: %d, y: %d, sprite: %s, type: %s, action: %s]", uuid, x, y,
					sprite, type.toString(), action.toString());
		}

	}

	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "map.entites";

	@JsonProperty("e")
	private List<Entity> entities = new ArrayList<>();

	public MapEntitiesMessage() {
		// no op.
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

	public List<Entity> getEntities() {
		return entities;
	}

	@Override
	public String toString() {
		return String.format("MapEntityMessage[accId: %d, entities: %s]", getAccountId(), entities.toString());
	}

	/**
	 * Sets the action for all included entities in this message. The action is
	 * used by the client how to react to a new entity (how to handle its
	 * animation).
	 * 
	 * @param action
	 */
	public void setAction(EntityAction action) {
		for (Entity e : entities) {
			e.setAction(action);
		}
	}

}
