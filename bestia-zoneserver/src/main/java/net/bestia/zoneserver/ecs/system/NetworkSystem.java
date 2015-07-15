package net.bestia.zoneserver.ecs.system;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.EntityUpdateMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.component.Visible;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.managers.GroupManager;
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
	
	@Wire
	private CommandContext ctx;
	
	private ComponentMapper<Visible> vcm;
	private ComponentMapper<PlayerControlled> pcm;
	
	private List<Entity> tempEntities = new ArrayList<>();
	
	private GroupManager groupManager;

	@SuppressWarnings("unchecked")
	public NetworkSystem() {
		super(Aspect.all(Visible.class));
		// TODO Auto-generated constructor stub
	}
	
	
	@Override
	protected void process(Entity e) {		
		final Visible visible = vcm.get(e);
		
		// Check if the visible entity has changed somehow.
		if(!visible.hasChanged) {
			return;
		}
		
		tempEntities.clear();
		
		// Now comes the tricky part. Get all player controlled bestias.
		ImmutableBag<Entity> clientEntities = groupManager.getEntities(PlayerControlSystem.CLIENT_GROUP);
		
		// Which are active...
		for(Entity client : clientEntities) {
			
			// TODO Check activity.
			
			tempEntities.add(client);
		}
		
		// And in sight...
		// TODO check sight
		
		// And send them the update of this entity.
		for(Entity client : tempEntities) {
			final PlayerControlled playerControlled = pcm.get(client);
			
			// Create EntityUpdate message, will with the informations.
			final EntityUpdateMessage updateMessage = new EntityUpdateMessage();
			
			// And send it.
			ctx.getServer().sendMessage(updateMessage);
		}
	}

}
