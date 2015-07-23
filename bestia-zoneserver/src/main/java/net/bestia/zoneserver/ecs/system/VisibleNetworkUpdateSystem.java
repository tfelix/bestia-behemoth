package net.bestia.zoneserver.ecs.system;

import java.util.UUID;

import net.bestia.messages.MapEntitiesMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Changable;
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
 * This system is responsible to keep track of appearing, disappearing and changing visual entities. Changes in such
 * entities must be reported to all player controlled and active entities near by.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class VisibleNetworkUpdateSystem extends EntityProcessingSystem {

	private final static Logger log = LogManager.getLogger(VisibleNetworkUpdateSystem.class);

	@Wire
	private CommandContext ctx;

	private ComponentMapper<Changable> changableMapper;
	private ComponentMapper<PlayerControlled> pcm;
	private ComponentMapper<Position> positionMapper;
	private ComponentMapper<Visible> visibleMapper;

	private UuidEntityManager uuidManager;

	private EntitySubscription playerSubscription;

	@SuppressWarnings("unchecked")
	public VisibleNetworkUpdateSystem() {
		super(Aspect.all(Visible.class));

	}

	@SuppressWarnings("unchecked")
	@Override
	protected void initialize() {

		AspectSubscriptionManager asm = world.getManager(AspectSubscriptionManager.class);
		playerSubscription = asm.get(Aspect.all(PlayerControlled.class, Active.class));

		subscription.addSubscriptionListener(new SubscriptionListener() {

			@Override
			public void removed(ImmutableBag<Entity> entities) {
				// TODO Auto-generated method stub

			}

			@Override
			public void inserted(ImmutableBag<Entity> entities) {
				log.info("=== NEW {} VISIBLE ENTITY === UPDATING ALL {} PLAYERS IN RANGE ===", entities.size(),
						playerSubscription.getEntities().size());

				// Find all players.
				final IntBag playersBag = playerSubscription.getEntities();

				for (Entity visibleEntity : entities) {
					for (int i = 0; i < playersBag.size(); i++) {
						final int id = playersBag.get(i);
						final Entity playerEntity = world.getEntity(id);

						// Is this player in sight of entity? If not continue.
						if (!isInSightDistance(playerEntity, visibleEntity)) {
							continue;
						}

						sendUpdate(playerEntity, visibleEntity);
					}
				}
			}
		});
	}

	@Override
	protected void process(Entity e) {
		// If the entitiy can not or has not changed then do nothing.
		final Changable changable = changableMapper.getSafe(e);
		if (changable == null || !changable.changed) {
			return;
		}

		// Find all players.
		final IntBag playersBag = playerSubscription.getEntities();

		for (int i = 0; i < playersBag.size(); i++) {
			final int id = playersBag.get(i);
			final Entity playerEntity = world.getEntity(id);

			// Is this player in sight of entity? If not continue.
			if (!isInSightDistance(playerEntity, e)) {
				continue;
			}

			sendUpdate(playerEntity, e);
		}
	}

	private boolean isInSightDistance(Entity playerEntity, Entity visibleEntity) {
		// TODO
		return true;
	}

	/**
	 * Creates a update message from a visible entity for a given player entity.
	 * 
	 * @param playerEntity
	 * @param visibleEntity
	 */
	private void sendUpdate(Entity playerEntity, Entity visibleEntity) {

		final PlayerControlled playerControlled = pcm.get(playerEntity);
		final long accId = playerControlled.playerBestia.getBestia().getOwner().getId();
			
		final UUID uuid = uuidManager.getUuid(visibleEntity);
		final Position pos = positionMapper.get(visibleEntity);
		final Visible visible = visibleMapper.get(visibleEntity);
		
		log.trace("Sending update for entity: {} to accId: {}", uuid.toString(), accId);

		final MapEntitiesMessage.Entity msg = new MapEntitiesMessage.Entity(uuid.toString(), pos.x, pos.y);
		msg.addSprite(visible.sprite);
		
		MapEntitiesMessage updateMsg = new MapEntitiesMessage();
		updateMsg.setAccountId(accId);
		
		updateMsg.getEntities().add(msg);
		
		ctx.getServer().sendMessage(updateMsg);
		
		markUnchanged(visibleEntity);		
	}
	
	private void markUnchanged(Entity e) {
		final Changable changable = changableMapper.getSafe(e);
		if(changable != null) {
			changable.changed = false;
		}
	}

}
