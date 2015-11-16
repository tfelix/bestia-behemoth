package net.bestia.zoneserver.ecs.system;

import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Visible;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.EntitySubscription.SubscriptionListener;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

/**
 * As soon as an active player bestia is spawned into the world it gets updated
 * with all entities in its sight.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire(injectInherited = true)
public class ActiveSpawnUpdateSystem extends NetworkUpdateSystem {

	public ActiveSpawnUpdateSystem() {
		super(Aspect.all(Active.class, PlayerBestia.class));
		setEnabled(false);
	}

	private static final Logger log = LogManager.getLogger(ActiveSpawnUpdateSystem.class);

	private AspectSubscriptionManager asm;

	@Wire
	private CommandContext ctx;

	private EntitySubscription visibleSubscription;

	@Override
	protected void initialize() {
		super.initialize();

		visibleSubscription = asm.get(Aspect.all(Visible.class));

		subscription.addSubscriptionListener(new SubscriptionListener() {

			@Override
			public void inserted(IntBag entities) {
				final IntBag visibleEntities = visibleSubscription.getEntities();

				log.trace("{} New active player, sending {} entities.", entities.size(), visibleEntities.size());

				for (int i = 0; i < entities.size(); i++) {
					final int newEntityId = entities.get(i);
					final Entity newActiveEntity = world.getEntity(newEntityId);

					for (int j = 0; j < visibleEntities.size(); j++) {
						final int visibleEntityId = visibleEntities.get(j);
						final Entity visibleEntity = world.getEntity(visibleEntityId);

						// TODO Do a range check.

						sendUpdate(newActiveEntity, visibleEntity, EntityAction.UPDATE);
					}
				}
			}

			@Override
			public void removed(IntBag entities) {
				// no op.
			}
		});
	}
}
