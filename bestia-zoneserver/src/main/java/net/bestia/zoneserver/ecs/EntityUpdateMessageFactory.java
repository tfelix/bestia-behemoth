package net.bestia.zoneserver.ecs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.World;
import com.artemis.managers.UuidEntityManager;
import com.artemis.utils.IntBag;

import net.bestia.messages.entity.EntityAction;
import net.bestia.messages.entity.EntityUpdateMessage;
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
	 * @param data.action
	 *            {@link EntityAction} of the message.
	 */
	public List<EntityUpdateMessage> createMessages(IntBag visibleEntities) {

		final List<EntityUpdateMessage> msgs = new ArrayList<>(visibleEntities.size());
		
		for (int i = 0; i < visibleEntities.size(); i++) {
			final int entityId = visibleEntities.get(i);
			final EntityUpdateMessage msg = createMessage(entityId);
			msgs.add(msg);
		}

		return msgs;
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
	public EntityUpdateMessage createMessage(int entityId) {

		final Entity e = world.getEntity(entityId);
		final UUID uuid = uuidManager.getUuid(e);
		final Visible visible = visibleMapper.get(entityId);
		final Vector2 pos = positionMapper.get(e).getPosition().getAnchor();
		final PlayerBestia playerControlled = playerMapper.getSafe(e);
		final SpriteType entityType = visible.spriteType;

		final EntityUpdateMessage msg = new EntityUpdateMessage(uuid.toString(), pos.x, pos.y);
		msg.addSprite(visible.sprite);
		msg.setType(entityType);

		if (playerControlled != null) {
			msg.setPlayerBestiaId(playerControlled.playerBestia.getPlayerBestiaId());
		}

		return msg;
	}
}
