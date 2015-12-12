package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.IntBag;

import net.bestia.messages.BestiaInfoMessage;
import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Changed;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.manager.NetworkUpdateManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

/**
 * This system looks for changed and visible entities and transmit the changes
 * to any active player in the visible range.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class ChangedNetworkUpdateSystem extends EntityProcessingSystem {

	//private static final Logger LOG = LogManager.getLogger(ChangedNetworkUpdateSystem.class);

	private EntitySubscription activePlayerEntities;
	private NetworkUpdateManager updateManager;
	private ComponentMapper<PlayerBestia> playerMapper;
	@Wire
	private CommandContext ctx;

	public ChangedNetworkUpdateSystem() {
		super(Aspect.all(Visible.class, Changed.class));

	}

	@Override
	protected void initialize() {
		final AspectSubscriptionManager asm = world.getSystem(AspectSubscriptionManager.class);
		activePlayerEntities = asm.get(Aspect.all(Active.class, PlayerBestia.class));
	}

	@Override
	protected void process(Entity e) {

		// First of all check if this is a player bestia entity. And if so send
		// the update to the corresponding player.
		if (playerMapper.has(e)) {
			final PlayerBestiaManager pbm = playerMapper.get(e).playerBestiaManager;
			final BestiaInfoMessage bestiaInfoMsg = new BestiaInfoMessage(pbm.getPlayerBestia(), pbm.getStatusPoints());
			ctx.getServer().sendMessage(bestiaInfoMsg);
		}

		// All active bestias on this zone.
		final IntBag entityIds = activePlayerEntities.getEntities();

		for (int i = 0; i < entityIds.size(); i++) {
			final Entity receiverEntity = world.getEntity(entityIds.get(i));
			
			if(!updateManager.isInSightDistance(receiverEntity, e)) {
				continue;
			}
			
			updateManager.sendUpdate(receiverEntity, e, EntityAction.UPDATE);
		}

		// Remove changed.
		e.edit().remove(Changed.class);
	}



}
