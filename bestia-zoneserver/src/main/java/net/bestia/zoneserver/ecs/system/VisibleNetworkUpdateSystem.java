package net.bestia.zoneserver.ecs.system;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Changable;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
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
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.ImmutableBag;

/**
 * This system is responsible to keep track of appearing, disappearing and changing visual entities. Changes in such
 * entities must be reported to all player controlled and active entities near by.
 * 
 * @author Thomas
 *
 */
@Wire
public class VisibleNetworkUpdateSystem extends EntityProcessingSystem {

	private final static Logger log = LogManager.getLogger(VisibleNetworkUpdateSystem.class);

	@Wire
	private CommandContext ctx;

	private ComponentMapper<Changable> changableMapper;
	
	private EntitySubscription playerSubscription;

	public VisibleNetworkUpdateSystem() {
		super(Aspect.all(Visible.class));

	}

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
				log.info("=== NEW {} VISIBLE ENTITY === UPDATING ALL {} PLAYERS IN RANGE ===", entities.size(), playerSubscription.getEntities().size());
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

		log.info("=== UPDATING ALL {} PLAYERS IN RANGE ===", playerSubscription.getEntities().size());
		
		
	}

}
