package net.bestia.zoneserver.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import net.bestia.messages.Message;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.entity.EntityContextActor;

/**
 * This class is used to give all entities a callback option in order to submit
 * messages back to the actor system and/or the connected clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityAkkaContext implements EntityContext {
	
	private final static Logger LOG = LoggerFactory.getLogger(EntityAkkaContext.class);

	private final ActorRef actor;

	/**
	 * Ctor.
	 * 
	 */
	public EntityAkkaContext() {

		final ActorSystem system = TypedActor.context().system();

		// Create a new entity context actor which is responsible for routing
		// the messages.
		actor = SpringExtension.actorOf(system, EntityContextActor.class);
	}

	/*
	 * 
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.zoneserver.entity.IEntityContext#sendMessage(net.bestia.
	 * messages.Message)
	 */
	@Override
	public void sendMessage(Message msg) {
		actor.tell(msg, ActorRef.noSender());
	}

	@Override
	public void entitySpawned(long entityId) {
		LOG.trace("Received entity spawn for entity id: {}", entityId);
		// NO OP.
	}
}