package net.bestia.zoneserver.ecs.system;

import java.util.UUID;

import net.bestia.messages.MapEntitiesMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.EntitySubscription.SubscriptionListener;
import com.artemis.annotations.Wire;
import com.artemis.managers.UuidEntityManager;
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.ImmutableBag;
import com.artemis.utils.IntBag;

/**
 * This system iterates over all player controlled entities (which are currently active). And send to them all other
 * entities which are in range.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class PlayerNetworkUpdateSystem extends EntityProcessingSystem {

	private static final Logger log = LogManager.getLogger(PlayerNetworkUpdateSystem.class);

	@Wire
	private CommandContext ctx;

	private ComponentMapper<PlayerControlled> pcm;
	private ComponentMapper<Position> positionMapper;
	private ComponentMapper<Visible> visibleMapper;

	private UuidEntityManager uuidManager;
	private AspectSubscriptionManager asm;

	private EntitySubscription visibleSubscription;

	@SuppressWarnings("unchecked")
	public PlayerNetworkUpdateSystem() {
		super(Aspect.all(PlayerControlled.class, Active.class));
		// no op.
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void initialize() {
		super.initialize();
		asm = world.getManager(AspectSubscriptionManager.class);
		visibleSubscription = asm.get(Aspect.all(Visible.class));

		subscription.addSubscriptionListener(new SubscriptionListener() {

			@Override
			public void removed(ImmutableBag<Entity> entities) {
				log.trace("New active and player controlled entities removed.");

			}

			@Override
			public void inserted(ImmutableBag<Entity> entities) {
				log.trace("New active and player controlled entities appeared.");

				for (Entity entity : entities) {
					sendAllVisibleInRange(entity);
				}

			}
		});
	};

	/**
	 * Sends all visible entities in the view distance to the given (player controlled) entity e.
	 * 
	 * @param playerEntity
	 *            Entity with Active and PlayerControlled component.
	 */
	private void sendAllVisibleInRange(Entity playerEntity) {

		final PlayerControlled pc = pcm.getSafe(playerEntity);

		if (pc == null) {
			log.warn("Given entity was NOT decorated with PlayerControlled component.");
			return;
		}

		final PlayerControlled playerControlled = pcm.get(playerEntity);
		final long accId = playerControlled.playerBestia.getBestia().getOwner().getId();

		final IntBag activeEntities = visibleSubscription.getEntities();
		log.trace("Found {} entities in sight.", activeEntities.size());

		// Dont waste a message if nothing is in sight.
		if (activeEntities.size() == 0) {
			return;
		}

		final MapEntitiesMessage entitiesMessage = new MapEntitiesMessage();
		entitiesMessage.setAccountId(accId);

		for (int i = 0; i < activeEntities.size(); i++) {
			final int id = activeEntities.get(i);
			final Entity visibleEntity = world.getEntity(id);

			final MapEntitiesMessage.Entity msg = getMessageFromEntity(visibleEntity);

			entitiesMessage.getEntities().add(msg);
		}

		ctx.getServer().sendMessage(entitiesMessage);
	}

	/**
	 * Converts a simple "map entity" from the ECS to a {@link MapEntitiesMessage.Entity}.
	 * 
	 * @param e
	 *            Entity to convert to a message for the client.
	 * @return A message containing all needed information about this entity for the client.
	 */
	private MapEntitiesMessage.Entity getMessageFromEntity(Entity e) {
		final UUID uuid = uuidManager.getUuid(e);
		final Position pos = positionMapper.get(e);
		final Visible visible = visibleMapper.get(e);

		final MapEntitiesMessage.Entity msg = new MapEntitiesMessage.Entity(uuid.toString(), pos.x, pos.y);

		msg.addSprite(visible.sprite);

		return msg;
	}

	@Override
	protected void process(Entity e) {
		// no op.
	}

	/**
	 * This system only acts via its trigger. It must no be processed.
	 */
	@Override
	protected boolean checkProcessing() {
		return false;
	}
}
