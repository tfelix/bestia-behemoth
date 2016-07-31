package net.bestia.zoneserver.actor.entity;

import akka.actor.UntypedActor;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.zoneserver.zone.entity.Entity;

/**
 * Upon receiving of a move message we will lookup the movable entity and sets
 * them to the new position.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MoveActor extends UntypedActor {

	@Override
	public void onReceive(Object message) throws Exception {
		
		if(message instanceof EntityMoveMessage) {
			
			final EntityMoveMessage msg = (EntityMoveMessage) message;
			
			final Entity entity = null;
			
			// TODO Was it a visible entity? If yes update all nearby entities.
			
			// TODO Was
			
		} else {
			unhandled(message);
		}
		
	}
}
