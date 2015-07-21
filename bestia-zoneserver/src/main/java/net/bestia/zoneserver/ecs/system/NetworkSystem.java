package net.bestia.zoneserver.ecs.system;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.EntityUpdateMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Changable;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.managers.GroupManager;
import com.artemis.managers.UuidEntityManager;
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.ImmutableBag;

/**
 * This system iterates over all player controlled entities (which are currently active). And send to them all other
 * entities which are in range.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class NetworkSystem extends EntityProcessingSystem {
	
	private static final Logger log = LogManager.getLogger(NetworkSystem.class);
	
	@Wire
	private CommandContext ctx;

	private ComponentMapper<PlayerControlled> pcm;
	private ComponentMapper<Active> activeMapper;
	private ComponentMapper<Position> ppm;
	private ComponentMapper<Changable> changableMapper;
	
	private UuidEntityManager uuidManager;
	
	private Set<Long> accountSet = new HashSet<>();
	private List<Entity> tempEntities = new ArrayList<>();
	
	private GroupManager groupManager;

	@SuppressWarnings("unchecked")
	public NetworkSystem() {
		super(Aspect.all(Visible.class, Active.class, Changable.class));
		// no op.
	}
	
	
	@Override
	protected void process(Entity e) {	
		
		accountSet.clear();
		tempEntities.clear();
		
		// Dont process non changed entity.
		final Changable changeComp = changableMapper.get(e);
		if(false == changeComp.changed) {
			return;
		}
		
		// Now comes the tricky part. Get all player controlled bestias.
		final ImmutableBag<Entity> clientEntities = groupManager.getEntities(PlayerControlSystem.CLIENT_GROUP);
		
		// Which are active...
		for(Entity client : clientEntities) {
			
			// Skip non active player entities.
			final Active activeComp = activeMapper.getSafe(e);
			if(activeComp == null) {
				continue;
			}
			
			tempEntities.add(client);
		}
		
		// And in sight...
		// TODO check sight
		
		// And send them the update of this entity.
		for(Entity client : tempEntities) {
			
			final PlayerControlled playerControlled = pcm.get(client);
			final Position pos = ppm.get(client);
			
			final long accId = playerControlled.playerBestia.getBestia().getOwner().getId();
			final int pbId = playerControlled.playerBestia.getBestia().getId();
			
			// Check if we have send this account an update already. If so skip this account entity.
			if(accountSet.contains(accId)) {
				continue;
			} else {
				accountSet.add(accId);
			}	
			
			final UUID uuid = uuidManager.getUuid(e);
			
			log.trace("Sending update message to account: {}", accId);
			
			// Create EntityUpdate message, will with the informations.
			final EntityUpdateMessage updateMessage = new EntityUpdateMessage(uuid.toString(), accId, pbId, pos.x, pos.y);
			
			// And send it.
			ctx.getServer().sendMessage(updateMessage);
		}
		
		changeComp.changed = false;
	}

}
