package net.bestia.zoneserver.ecs.system;

import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.manager.NetworkUpdateManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.BaseEntitySystem;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.EntitySubscription.SubscriptionListener;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

@Wire
public class VisibleSpawnUpdateSystem extends BaseEntitySystem {

	public VisibleSpawnUpdateSystem() {
		super(Aspect.all(Visible.class, Position.class));
		
		setEnabled(false);
	}

	private static final Logger log = LogManager.getLogger(VisibleSpawnUpdateSystem.class);

	@Wire
	private CommandContext ctx;

	private NetworkUpdateManager updateManager;
	private EntitySubscription playerSubscription;

	@Override
	protected void initialize() {
		super.initialize();

		final AspectSubscriptionManager asm = world.getSystem(AspectSubscriptionManager.class);

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
						if (!updateManager.isInSightDistance(playerEntity, visibleEntity)) {
							continue;
						}

						updateManager.sendUpdate(playerEntity, visibleEntity, EntityAction.APPEAR);
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
	protected void processSystem() {
		// TODO Auto-generated method stub
		
	}
}
