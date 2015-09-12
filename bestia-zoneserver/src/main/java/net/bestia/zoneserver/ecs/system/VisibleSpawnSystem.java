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
import com.artemis.EntitySubscription.SubscriptionListener;
import com.artemis.EntitySystem;
import com.artemis.annotations.Wire;
import com.artemis.utils.ImmutableBag;
import com.artemis.utils.IntBag;

@Wire
public class VisibleSpawnSystem extends EntitySystem {

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

		playerSubscription = world.getManager(AspectSubscriptionManager.class).get(
				Aspect.all(Active.class));
		
		subscription.addSubscriptionListener(new SubscriptionListener() {

			@Override
			public void inserted(ImmutableBag<Entity> entities) {
				log.info("### {} NEW VISIBLE, UPDATING {} PLAYERS ###", entities.size(), playerSubscription.getEntities().size());
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
