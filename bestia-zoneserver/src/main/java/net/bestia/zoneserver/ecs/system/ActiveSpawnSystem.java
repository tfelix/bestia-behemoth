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
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.ImmutableBag;
import com.artemis.utils.IntBag;

@Wire
public class ActiveSpawnSystem extends EntityProcessingSystem {

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
		
		visibleSubscription = asm.get(Aspect.all(Visible.class));

		subscription.addSubscriptionListener(new SubscriptionListener() {

			@Override
			public void inserted(IntBag entities) {
				final IntBag visibleEntities = visibleSubscription.getEntities();
				log.info("### {} NEW ACTIVE PLAYER, SENDING {} visible entities. ###", entities.size(), visibleEntities.size());
			}

			@Override
			public void removed(IntBag entities) {
				// TODO Auto-generated method stub
				
			}
		});
	}
	/*
	@Override
	protected boolean checkProcessing() {
		return false;
	}*/

	@Override
	protected void process(Entity e) {
		// TODO Auto-generated method stub
		
	}
}
