package net.bestia.zoneserver.ecs.system;

import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Changable;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.component.Visible;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.Entity;
import com.artemis.EntitySubscription.SubscriptionListener;
import com.artemis.annotations.Wire;
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
public class VisibleNetworkUpdateSystem extends NetworkUpdateSystem {

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

						sendUpdate(playerEntity, visibleEntity, EntityAction.APPEAR);
					}
				}
			}
		});
	}

	@Override
	protected void process(Entity e) {
		// If the entity can not or has not changed then do nothing.
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

			sendUpdate(playerEntity, e, EntityAction.UPDATE);
		}
	}

}
