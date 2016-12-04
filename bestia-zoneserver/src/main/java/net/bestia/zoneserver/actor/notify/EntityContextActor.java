package net.bestia.zoneserver.actor.notify;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.messages.entity.EntityPositionMessage;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * This actor is responsible for receiving messages from the entities and
 * sending them to the appropriate sub actors which handle the messages. It is
 * the central entry point for sending messages from entities back to connected
 * users. But it is also used for entity to entity communication.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityContextActor extends BestiaActor {
	
	public static final String NAME = "entityContext";

	private final PlayerEntityService peService;

	@Autowired
	public EntityContextActor(PlayerEntityService entityService) {

		this.peService = Objects.requireNonNull(entityService);
	}

	@Override
	public void onReceive(Object msg) throws Throwable {
		
		// Check which message it is.
		if(msg instanceof EntityPositionMessage) {
			EntityPositionMessage posMsg = (EntityPositionMessage) msg;
			//peService.getActiveAccountIdsInRange(range)
		}
	}

}
