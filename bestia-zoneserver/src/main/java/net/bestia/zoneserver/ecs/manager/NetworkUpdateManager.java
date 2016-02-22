package net.bestia.zoneserver.ecs.manager;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.managers.UuidEntityManager;
import com.artemis.utils.IntBag;

import net.bestia.messages.MapEntitiesMessage;
import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.messages.MapEntitiesMessage.EntityType;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Item;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Should be abstract.
 * 
 * @deprecated In zukunft den MessageManager verwenden.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class NetworkUpdateManager extends BaseSystem {

	private static final Logger LOG = LogManager.getLogger(NetworkUpdateManager.class);

	@Wire
	private CommandContext ctx;

	private ComponentMapper<PlayerBestia> playerMapper;
	private ComponentMapper<Visible> visibleMapper;
	private ComponentMapper<Position> positionMapper;
	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<Item> itemMapper;

	private UuidEntityManager uuidManager;
	private EntitySubscription activePlayers;

	public NetworkUpdateManager() {
		setEnabled(false);
	}

	@Override
	protected void initialize() {
		super.initialize();

		// Get the active player subscription.
		final AspectSubscriptionManager subManager = world.getAspectSubscriptionManager();
		activePlayers = subManager.get(Aspect.all(Active.class, PlayerBestia.class));
	}

	/**
	 * Checks if one entity is in sight of another.
	 * 
	 * @param playerEntity
	 * @param visibleEntity
	 * @return
	 */
	public boolean isInSightDistance(Entity playerEntity, Entity visibleEntity) {
		return isInSightDistance(playerEntity.getId(), visibleEntity.getId());
	}

	/**
	 * Checks if one entity is in sight of another.
	 * 
	 * @param playerEntity
	 * @param visibleEntity
	 * @return
	 */
	public boolean isInSightDistance(int playerEntity, int visibleEntity) {
		// TODO
		return true;
	}

	/**
	 * Returns all player in sight (network receive range) denoted by a
	 * entityId. This id must have at least a {@link Position} component in
	 * order to be located inside the ECS. Only active players are located.
	 * 
	 * @param entityId
	 * @return
	 */
	public IntBag getActivePlayerInSight(int entityId) {
		final Position pos = positionMapper.getSafe(entityId);
		if (pos == null) {
			LOG.warn("Entity id {} has no position component.", entityId);
			return new IntBag(0);
		}
		
		final IntBag result = new IntBag();
		
		// TODO Das hier später über einen Quadtree abfragen statt pauschal alle
		// entities holen.
		final IntBag actives = activePlayers.getEntities();
		for (int i = 0; i < actives.size(); i++) {
			final int activeId = actives.get(i);
			
			if(!isInSightDistance(activeId, entityId)) {
				continue;
			}
			
			result.add(activeId);
		}
		
		return result;
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

		if (bestiaMapper.has(e)) {
			return EntityType.BESTIA;
		} else if (itemMapper.has(e)) {
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