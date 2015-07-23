package net.bestia.messages;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This message contains a list of (visible) entities around a player. These entities consist out of a sprite, a
 * position and additional data that should be preloaded and is associated with an entity. For example existing sounds
 * or attack animations which can be triggered spontaneously. These should be loaded as soon as possible from the client
 * engine.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapEntitiesMessage extends Message {

	/**
	 * Describes how the client can interact with the entity as a guideline.
	 *
	 */
	public enum EntityType {
		LOOT, INTERACT, ATTACK
	}

	/**
	 * Certain actions can not be told to the client via an entity message alone. Some actions like a disappearing
	 * entity must be send via this special action marker.
	 *
	 */
	public enum EntityAction {
		APPEAR, DIE, VANISH
	}

	/**
	 * Describes an entity on the map for the engine to display.
	 */
	public class Entity {

		private String uuid;
		private List<String> sprites = new ArrayList<>();
		private int x;
		private int y;
		private EntityType type;
		private EntityAction action = EntityAction.APPEAR;

		public Entity() {

		}

		public Entity(String uuid, int x, int y) {
			this.uuid = uuid;
			this.x = x;
			this.y = y;
		}

		public String getUuid() {
			return uuid;
		}

		public void setUuid(String uuid) {
			this.uuid = uuid;
		}

		public List<String> getSprites() {
			return sprites;
		}

		public void setSprites(List<String> sprites) {
			this.sprites = sprites;
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

		public EntityType getType() {
			return type;
		}

		public void setType(EntityType type) {
			this.type = type;
		}

		@Override
		public String toString() {
			return String.format("Entity[uuid: %s, x: %d, y: %d, sprites: %s, type: %s, action: %s]", uuid, x, y,
					sprites.toString(), type.toString(), action.toString());
		}

	}

	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "map.entites";

	@JsonProperty("e")
	private List<Entity> entities;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

	@Override
	public String toString() {
		return String.format("MapEntityMessage[accId: %d, entities: %s]", getAccountId(), entities.toString());
	}

}
