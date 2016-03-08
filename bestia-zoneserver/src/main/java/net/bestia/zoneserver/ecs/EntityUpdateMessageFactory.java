package net.bestia.zoneserver.ecs;

import java.util.UUID;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.World;
import com.artemis.managers.UuidEntityManager;
import com.artemis.utils.IntBag;

import net.bestia.messages.MapEntitiesMessage;
import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.messages.entity.SpriteType;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Generates update messages from an entity.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityUpdateMessageFactory {

	private final World world;
	private final UuidEntityManager uuidManager;
	private final ComponentMapper<Visible> visibleMapper;
	private final ComponentMapper<Position> positionMapper;
	private final ComponentMapper<PlayerBestia> playerMapper;
	private final IntBag helperBag = new IntBag(1);

	public EntityUpdateMessageFactory(World world) {
		this.world = world;
		this.uuidManager = world.getSystem(UuidEntityManager.class);
		this.visibleMapper = world.getMapper(Visible.class);
		this.positionMapper = world.getMapper(Position.class);
		this.playerMapper = world.getMapper(PlayerBestia.class);
	}

	/**
	 * Creates a update message for several visible entities for a given player
	 * entity.
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

	/**
	 * Creates a single update message for an visible entity.
	 * 
	 * @param visibleEntity
	 * @return
	 */
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
	private MapEntitiesMessage.Entity getMessageFromEntity(int entityId) {
		final Entity e = world.getEntity(entityId);
		final UUID uuid = uuidManager.getUuid(e);
		final Visible visible = visibleMapper.get(entityId);
		final Vector2 pos = positionMapper.get(e).getPosition().getAnchor();
		final PlayerBestia playerControlled = playerMapper.getSafe(e);
		final SpriteType entityType = visible.spriteType;

		final MapEntitiesMessage.Entity msg = new MapEntitiesMessage.Entity(uuid.toString(), pos.x, pos.y);
		msg.addSprite(visible.sprite);
		msg.setType(entityType);

		if (playerControlled != null) {
			msg.setPlayerBestiaId(playerControlled.playerBestiaManager.getPlayerBestiaId());
		}

		return msg;
	}
}
