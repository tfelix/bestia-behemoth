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
public class VisibleSpawnSystem extends NetworkUpdateSystem {

	public VisibleSpawnSystem() {
		super(Aspect.all(Visible.class));
		setPassive(true);
	}

	private static final Logger log = LogManager.getLogger(VisibleSpawnSystem.class);

	@Wire
	private CommandContext ctx;

	private EntitySubscription playerSubscription;

	@Override
	protected void initialize() {
		super.initialize();
		
		// Workaround must be set since parent gets no wireing.
		setCommandContext(ctx);

		final AspectSubscriptionManager asm = world.getManager(AspectSubscriptionManager.class);

		playerSubscription = asm.get(Aspect.all(Active.class, PlayerBestia.class));

		subscription.addSubscriptionListener(new SubscriptionListener() {

			@Override
			public void inserted(IntBag entities) {

				final IntBag playersBag = playerSubscription.getEntities();

				log.trace("### {} NEW VISIBLE, UPDATING {} PLAYERS ###", entities.size(), playersBag.size());

				for (int i = 0; i < entities.size(); i++) {
					final Entity visibleEntity = world.getEntity(entities.get(i));
					for (int j = 0; j < playersBag.size(); j++) {
						final int id = playersBag.get(j);
						final Entity playerEntity = world.getEntity(id);

						// Is this player in sight of entity? If not continue.
						if (!isInSightDistance(playerEntity, visibleEntity)) {
							continue;
						}

						sendUpdate(playerEntity, visibleEntity, EntityAction.APPEAR);
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
