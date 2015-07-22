package net.bestia.zoneserver.ecs.system;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.EntityUpdateMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Changable;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.Aspect.Builder;
import com.artemis.EntitySubscription.SubscriptionListener;
import com.artemis.annotations.Wire;
import com.artemis.managers.GroupManager;
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
public class PlayerNetworkSystem extends EntityProcessingSystem {

	private static final Logger log = LogManager.getLogger(PlayerNetworkSystem.class);

	@Wire
	private CommandContext ctx;

	private ComponentMapper<PlayerControlled> pcm;
	private ComponentMapper<Position> ppm;
	private ComponentMapper<Changable> changableMapper;
	private ComponentMapper<Visible> visibleMapper;

	private UuidEntityManager uuidManager;
	private AspectSubscriptionManager asm;
	
	private EntitySubscription visibleSubscription;

	private Set<Long> accountSet = new HashSet<>();
	private List<Entity> tempEntities = new ArrayList<>();

	private GroupManager groupManager;

	public PlayerNetworkSystem() {
		super(Aspect.all(PlayerControlled.class, Active.class));
		// no op.
	}

	
	@Override
	protected void initialize() {
		super.initialize();
		asm = world.getManager(AspectSubscriptionManager.class);
		visibleSubscription = asm.get(Aspect.all(Visible.class));

		subscription.addSubscriptionListener(new SubscriptionListener() {
			
			@Override
			public void removed(ImmutableBag<Entity> entities) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void inserted(ImmutableBag<Entity> entities) {
				log.info("NEW PLAYER ACTIVE ENTITIES APPEARED. Send all other visible entities in sight.");
				
				for (Entity entity : entities) {
					sendAllVisibleInRange(entity);
				}
				
			}
		});
	};

	/**
	 * Sends all visible entities in the view distance to the given (player controlled) entity e.
	 * 
	 * @param e
	 */
	private void sendAllVisibleInRange(Entity e) {

		final PlayerControlled pc = pcm.getSafe(e);

		if (pc == null) {
			log.warn("Given entity was NOT decorated with PlayerControlled component.");
			return;
		}

		final PlayerControlled playerControlled = pcm.get(e);
		final long accId = playerControlled.playerBestia.getBestia().getOwner().getId();
		final int pbId = playerControlled.playerBestia.getBestia().getId();

		IntBag activeEntities = visibleSubscription.getEntities();
		
		log.info("FOUND {} ENTITIES IN SIGHT", activeEntities.size());

		/*for (int i = 0; i < activeEntities.size(); i++) {
			int id = activeEntities.get(i);

			final Entity visibleEntity = world.getEntity(id);

			final UUID uuid = uuidManager.getUuid(visibleEntity);

			// Check if it is player controlled

			// Add the visible information.
			final Visible visible = visibleMapper.get(e);
			String sprite = visible.sprite;

			// And send it.
			final EntityUpdateMessage updateMessage = new EntityUpdateMessage(uuid.toString(), accId, pbId, 0, 0);
			ctx.getServer().sendMessage(updateMessage);
		}*/

	}

	@Override
	protected void process(Entity e) {
/*
		accountSet.clear();
		tempEntities.clear();

		// Get all visible and changed entities in the view range.
		IntBag visibleEntities = visibleSubscription.getEntities();
		for(int i = 0; i < visibleEntities.size(); i++) {
			final int id = visibleEntities.get(i);
			Entity visibleEntity = world.getEntity(id);
			
			final Changable changable = changableMapper.getSafe(visibleEntity);
			if(changable == null || !changable.changed) {
				continue;
			}
			
			// And in sight... TODO
			
			tempEntities.add(visibleEntity);
		}

		final PlayerControlled playerControlled = pcm.get(e);
		final long accId = playerControlled.playerBestia.getBestia().getOwner().getId();
		final int pbId = playerControlled.playerBestia.getBestia().getId();

		// And send them the update of this entity.
		for (Entity visibleEntity : tempEntities) {

			
			final Position pos = ppm.get(visibleEntity);
			final UUID uuid = uuidManager.getUuid(visibleEntity);

			log.trace("Sending update message to account: {}", accId);

			// Create EntityUpdate message, will with the informations.
			final EntityUpdateMessage updateMessage = new EntityUpdateMessage(uuid.toString(), accId, pbId, pos.x,
					pos.y);

			// And send it.
			ctx.getServer().sendMessage(updateMessage);
		}*/
	}

}
