package net.bestia.zoneserver.ecs.manager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;

@Wire
public class MessageManager extends BaseSystem {
	
	private static final Logger LOG = LogManager.getLogger(MessageManager.class);
	
	private ComponentMapper<PlayerBestia> playerBestiaMapper;
	private ComponentMapper<Position> posMapper;

	@Wire
	private PlayerBestiaSpawnManager playerBestiaSpawnManager;
	
	@Wire
	private CommandContext ctx;

	/**
	 * Sends the given message to all active bestias in sight.
	 * 
	 * @param source
	 *            The entity from which the sight range will be calculated.
	 * @param msg
	 */
	public void sendMessageToSightrange(int source, Message msg) {
		final Position pos = posMapper.getSafe(source);
		
		if(pos == null) {
			LOG.warn("Entity has no position. Can not send messages.");
			return;
		}
		
		final IntBag receivers = playerBestiaSpawnManager.getActivePlayersInSight(pos);
		
		for(int i = 0; i < receivers.size(); i++) {
			final int receiverId = receivers.get(i);
			
			final PlayerBestia pb = playerBestiaMapper.get(receiverId);
			final long accId = pb.playerBestiaManager.getAccountId();
			msg.setAccountId(accId);
			
			ctx.getServer().sendMessage(msg);
		}
	}

	@Override
	protected void initialize() {
		
		setEnabled(false);
	}

	@Override
	protected void processSystem() {
		// no op (disabled)
	}

}
