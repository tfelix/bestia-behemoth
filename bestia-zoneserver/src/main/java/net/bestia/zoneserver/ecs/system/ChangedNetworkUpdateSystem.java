package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;

import net.bestia.messages.EntityPositionUpdateMessage;
import net.bestia.messages.bestia.BestiaInfoMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Changed;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.manager.PlayerBestiaEntityProxy;

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

	private PlayerBestiaSpawnManager playerSpawnManager;
	private ComponentMapper<PlayerBestia> playerMapper;
	@Wire
	private CommandContext ctx;

	public ChangedNetworkUpdateSystem() {
		super(Aspect.all(Visible.class, Changed.class));
	}

	@Override
	protected void process(Entity e) {

		// First of all check if this is a player bestia entity. And if so send
		// the update to the corresponding player.
		if (playerMapper.has(e)) {
			final PlayerBestiaEntityProxy pbm = playerMapper.get(e).playerBestiaManager;
			final BestiaInfoMessage bestiaInfoMsg = new BestiaInfoMessage(pbm.getPlayerBestia(), pbm.getStatusPoints());
			ctx.getServer().sendMessage(bestiaInfoMsg);
		}
		
		// TODO HIER MESSAGE ERZEUGEN.
		final EntityPositionUpdateMessage updateMsg = null;
		playerSpawnManager.sendMessageToSightrange(e.getId(), updateMsg);

		// Remove changed.
		e.edit().remove(Changed.class);
	}



}
