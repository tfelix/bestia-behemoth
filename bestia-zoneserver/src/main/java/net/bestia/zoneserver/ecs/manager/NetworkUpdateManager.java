package net.bestia.zoneserver.ecs.manager;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect.Builder;
import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.managers.UuidEntityManager;

import net.bestia.messages.MapEntitiesMessage;
import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.messages.MapEntitiesMessage.EntityType;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Item;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Should be abstract.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class NetworkUpdateManager extends BaseEntitySystem {

	private static final Logger LOG = LogManager.getLogger(NetworkUpdateManager.class);

	@Wire
	private CommandContext ctx;

	private ComponentMapper<PlayerBestia> playerMapper;
	private ComponentMapper<Visible> visibleMapper;
	private ComponentMapper<Position> positionMapper;
	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<Item> itemMapper;

	private UuidEntityManager uuidManager;

	public NetworkUpdateManager(Builder aspect) {
		super(aspect);
		setEnabled(false);
	}

	/**
	 * Checks if one entity is in sight of another.
	 * 
	 * @param playerEntity
	 * @param visibleEntity
	 * @return
	 */
	public boolean isInSightDistance(Entity playerEntity, Entity visibleEntity) {
		// TODO
		return true;
	}

	/**
	 * Creates a update message from a visible entity for a given player entity.
	 * 
	 * @param playerEntity
	 * @param visibleEntit
	 * @param action
	 *            {@link EntityAction} of the message.
	 */
	public void sendUpdate(Entity playerEntity, Entity visibleEntity, EntityAction action) {

		final PlayerBestia playerControlled = playerMapper.getSafe(playerEntity);
		final long accId = playerControlled.playerBestiaManager.getAccountId();

		final MapEntitiesMessage.Entity msg = getMessageFromEntity(visibleEntity, action);
		final MapEntitiesMessage updateMsg = new MapEntitiesMessage();

		updateMsg.setAccountId(accId);
		updateMsg.getEntities().add(msg);

		LOG.trace("Sending update for entity: {} to accId: {}", msg.getUuid(), accId);

		ctx.getServer().sendMessage(updateMsg);
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
	protected MapEntitiesMessage.Entity getMessageFromEntity(Entity e, EntityAction action) {
		final UUID uuid = uuidManager.getUuid(e);
		final Visible visible = visibleMapper.get(e);
		final Vector2 pos = positionMapper.get(e).position.getAnchor();
		final PlayerBestia playerControlled = playerMapper.getSafe(e);

		final MapEntitiesMessage.Entity msg = new MapEntitiesMessage.Entity(uuid.toString(), pos.x, pos.y);
		msg.setAction(action);
		msg.addSprite(visible.sprite);
		msg.setType(getTypeFromEntity(e));
		
		if (playerControlled != null) {
			msg.setPlayerBestiaId(playerControlled.playerBestiaManager.getPlayerBestiaId());
		}

		return msg;
	}

	/**
	 * Tries to guess the type of the entity which is represented with this
	 * entity.
	 * 
	 * @param e
	 * @return
	 */
	protected MapEntitiesMessage.EntityType getTypeFromEntity(Entity e) {
		
		if(bestiaMapper.has(e)) {
			return EntityType.BESTIA;
		} else if(itemMapper.has(e)) {
			return EntityType.LOOT;
		} else {
			return EntityType.NONE;
		}
		
	}

	@Override
	protected void processSystem() {
		// is not processed (enabled: false)
	}

}