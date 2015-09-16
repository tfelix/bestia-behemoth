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

@Wire
public class ActiveSpawnSystem extends NetworkUpdateSystem {

	public ActiveSpawnSystem() {
		super(Aspect.all(Active.class, PlayerBestia.class));
		setPassive(true);
	}

	private static final Logger log = LogManager.getLogger(ActiveSpawnSystem.class);

	private AspectSubscriptionManager asm;

	@Wire
	private CommandContext ctx;

	private EntitySubscription visibleSubscription;

	@Override
	protected void initialize() {
		super.initialize();

		// Workaround must be set since parent gets no wireing.
		setCommandContext(ctx);

		visibleSubscription = asm.get(Aspect.all(Visible.class));

		subscription.addSubscriptionListener(new SubscriptionListener() {

			@Override
			public void inserted(IntBag entities) {
				final IntBag visibleEntities = visibleSubscription.getEntities();

				log.trace("{} New active player, sending {} entities.", entities.size(), visibleEntities.size());

				for (int i = 0; i < entities.size(); i++) {
					final int newEntityId = entities.get(i);
					final Entity newActiveEntity = world.getEntity(newEntityId);

					for(int j = 0; j < visibleEntities.size(); j++) {
						final int visibleEntityId = visibleEntities.get(j);
						final Entity visibleEntity = world.getEntity(visibleEntityId);
						
						// TODO Do a range check.
						
						sendUpdate(newActiveEntity, visibleEntity, EntityAction.APPEAR);
					}
				}
			}

			@Override
			public void removed(IntBag entities) {
				// no op.
			}
		});
	}

	@Override
	protected void process(Entity e) {
		// no op.
	}
}
