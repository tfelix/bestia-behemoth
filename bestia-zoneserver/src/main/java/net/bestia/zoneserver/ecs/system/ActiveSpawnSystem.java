package net.bestia.zoneserver.ecs.system;

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
import com.artemis.EntitySystem;
import com.artemis.EntitySubscription.SubscriptionListener;
import com.artemis.annotations.Wire;
import com.artemis.utils.ImmutableBag;
import com.artemis.utils.IntBag;

@Wire
public class ActiveSpawnSystem extends EntitySystem {

	public ActiveSpawnSystem() {
		super(Aspect.all(Active.class, PlayerBestia.class));
		setPassive(true);
	}

	private static final Logger log = LogManager.getLogger(ActiveSpawnSystem.class);

	@Wire
	private CommandContext ctx;

	private EntitySubscription visibleSubscription;

	@Override
	protected void initialize() {

		visibleSubscription = world.getManager(AspectSubscriptionManager.class).get(Aspect.all(Visible.class));

		subscription.addSubscriptionListener(new SubscriptionListener() {

			@Override
			public void inserted(ImmutableBag<Entity> entities) {
				final IntBag visibleEntities = visibleSubscription.getEntities();
				log.info("### {} NEW PLAYER, SENDING {} visible entities. ###", entities.size(), visibleEntities.size());
			}

			@Override
			public void removed(ImmutableBag<Entity> entities) {
				// TODO Auto-generated method stub
				
			}
		});
	}

	@Override
	protected void processSystem() {

	}
}
