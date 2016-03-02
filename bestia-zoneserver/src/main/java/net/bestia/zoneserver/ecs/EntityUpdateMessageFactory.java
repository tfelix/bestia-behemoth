package net.bestia.zoneserver.ecs;

import java.util.UUID;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.World;
import com.artemis.managers.UuidEntityManager;
import com.artemis.utils.IntBag;

import net.bestia.messages.MapEntitiesMessage;
import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.messages.MapEntitiesMessage.EntityType;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Item;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Generates update messages from an entity.
 * 
 * @author Thomas
 *
 */
public class EntityUpdateMessageFactory {

	private final World world;
	private final UuidEntityManager uuidManager;
	private final ComponentMapper<Bestia> bestiaMapper;
	private final ComponentMapper<Visible> visibleMapper;
	private final ComponentMapper<Position> positionMapper;
	private final ComponentMapper<Item> itemMapper;
	private final ComponentMapper<PlayerBestia> playerMapper;
	private final IntBag helperBag = new IntBag(1);

	public EntityUpdateMessageFactory(World world) {
		this.world = world;
		this.uuidManager = world.getSystem(UuidEntityManager.class);
		this.bestiaMapper = world.getMapper(Bestia.class);
		this.visibleMapper = world.getMapper(Visible.class);
		this.positionMapper = world.getMapper(Position.class);
		this.itemMapper = world.getMapper(Item.class);
		this.playerMapper = world.getMapper(PlayerBestia.class);
	}

	/**
	 * Creates a update message from a visible entity for a given player entity.
	 * 
	 * @param playerEntity
	 * @param visibleEntit
	 * @param action
	 *            {@link EntityAction} of the message.
	 */
	public MapEntitiesMessage createMessage(IntBag visibleEntities) {

		final MapEntitiesMessage updateMsg = new MapEntitiesMessage();

		for (int i = 0; i < visibleEntities.size(); i++) {
			final int entityId = visibleEntities.get(i);
			final MapEntitiesMessage.Entity msg = getMessageFromEntity(entityId);
			updateMsg.getEntities().add(msg);
		}

		return updateMsg;
	}

	public MapEntitiesMessage createMessage(int visibleEntity) {

		helperBag.clear();
		helperBag.add(visibleEntity);

		return createMessage(helperBag);
	}

	/**
	 * Converts a simple "map entity" from the ECS to a
	 * {@link MapEntitiesMessage.Entity}.
	 * 
	 * @param e
	 *            Entity to convert to a message for the client.
	 * @return A message containing all needed information about this entity for
	 *         the client.
	 */
	protected MapEntitiesMessage.Entity getMessageFromEntity(int entityId) {
		final Entity e = world.getEntity(entityId);
		final UUID uuid = uuidManager.getUuid(e);
		final Visible visible = visibleMapper.get(entityId);
		final Vector2 pos = positionMapper.get(e).position.getAnchor();
		final PlayerBestia playerControlled = playerMapper.getSafe(e);
		final MapEntitiesMessage.EntityType entityType = getTypeFromEntity(entityId);

		final MapEntitiesMessage.Entity msg = new MapEntitiesMessage.Entity(uuid.toString(), pos.x, pos.y);
		msg.addSprite(visible.sprite);
		msg.setType(entityType);

		if (playerControlled != null) {
			msg.setPlayerBestiaId(playerControlled.playerBestiaManager.getPlayerBestiaId());
		}

		return msg;
	}

	// TODO Das hier sollte VISIBLE Bestimmen um welchen Typ von Sprite es sich
	// handelt.

	/**
	 * Tries to guess the type of the entity which is represented with this
	 * entity. By this the client determines which kind of sprite system should
	 * be used. This is better suited for the visible.class compoenent.
	 * 
	 * @param e
	 * @return
	 */
	private MapEntitiesMessage.EntityType getTypeFromEntity(int entityId) {

		if (bestiaMapper.has(entityId)) {
			return EntityType.BESTIA;
		} else if (itemMapper.has(entityId)) {
			return EntityType.LOOT;
		} else {
			return EntityType.NONE;
		}

	}
}
