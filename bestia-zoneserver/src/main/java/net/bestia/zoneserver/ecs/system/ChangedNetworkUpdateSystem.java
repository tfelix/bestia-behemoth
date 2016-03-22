package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.bestia.BestiaInfoMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Changed;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;

/**
 * This system looks for changed and visible entities and transmit the changes
 * to any active player in the visible range.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class ChangedNetworkUpdateSystem extends IteratingSystem {

	// private static final Logger LOG =
	// LogManager.getLogger(ChangedNetworkUpdateSystem.class);

	private PlayerBestiaSpawnManager playerSpawnManager;
	private ComponentMapper<PlayerBestia> playerMapper;
	private ComponentMapper<Bestia> bestiaMapper;
	
	@Wire
	private CommandContext ctx;

	public ChangedNetworkUpdateSystem() {
		super(Aspect.all(Visible.class, Changed.class));
	}

	/**
	 * If the visible tag is removed and thus the entity does not get matched
	 * with this system anymore we need to notify the system immediately.
	 */
	@Override
	protected void removed(int entityId) {

	}

	@Override
	protected void process(int entityId) {

		// First of all check if this is a player bestia entity. And if so send
		// the update to the corresponding player.
		if (playerMapper.has(entityId)) {
			final PlayerBestiaEntityProxy pbm = playerMapper.get(entityId).playerBestia;
			final BestiaInfoMessage bestiaInfoMsg = new BestiaInfoMessage(pbm.getPlayerBestia(), pbm.getStatusPoints());
			ctx.getServer().sendMessage(bestiaInfoMsg);
		}

		// We need to create the update message via different means. If it is a
		// bestia it can create the message itself.
		final AccountMessage updateMsg;
		if(bestiaMapper.has(entityId)) {
			updateMsg = bestiaMapper.get(entityId).manager.getUpdateMessage();
		} else {
			// If it is something different we might to create the update message by hand.
			throw new UnsupportedOperationException("Currently only bestia entities are allowed.");
		}
		
		playerSpawnManager.sendMessageToSightrange(entityId, updateMsg);

		// Remove changed.
		world.getEntity(entityId).edit().remove(Changed.class);
	}

}
