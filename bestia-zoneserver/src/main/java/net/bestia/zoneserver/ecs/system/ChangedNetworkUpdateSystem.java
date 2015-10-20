package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Changed;
import net.bestia.zoneserver.ecs.component.Collision;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Visible;

/**
 * This system looks for changed and visible entities and transmit the changes
 * to any active player in the visible range.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire(injectInherited = true)
public class ChangedNetworkUpdateSystem extends NetworkUpdateSystem {
	
	//private ComponentMapper<PlayerBestia> playerMapper;

	private EntitySubscription activePlayerEntities;

	public ChangedNetworkUpdateSystem() {
		super(Aspect.all(Visible.class, Changed.class, Collision.class));
		// no op.
	}

	@Override
	protected void initialize() {
		final AspectSubscriptionManager asm = world.getManager(AspectSubscriptionManager.class);
		activePlayerEntities = asm.get(Aspect.all(Active.class, PlayerBestia.class));
	}

	@Override
	protected void process(Entity e) {

		// All active bestias on this zone.
		final IntBag entityIds = activePlayerEntities.getEntities();

		for (int i = 0; i < entityIds.size(); i++) {
			final Entity receiverEntity = world.getEntity(entityIds.get(i));

			// TODO Are they in sight range of e?
			
			sendUpdate(receiverEntity, e, EntityAction.UPDATE);
		}
		
		// Remove changed.
		e.edit().remove(Changed.class);
	}

}
