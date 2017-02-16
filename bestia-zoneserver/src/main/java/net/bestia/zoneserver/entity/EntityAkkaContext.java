package net.bestia.zoneserver.entity;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.TypedActor;
import net.bestia.messages.Message;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.SpringExtension.SpringExt;
import net.bestia.zoneserver.actor.entity.EntityContextActor;

/**
 * This class is used to give all entities a callback option in order to submit
 * messages back to the actor system and/or the connected clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityAkkaContext implements EntityContext {

	private final ActorRef actor;

	/**
	 * Ctor.
	 * 
	 */
	public EntityAkkaContext() {

		final ActorContext context = TypedActor.context();

		// Create a new entity context actor which is responsible for routing
		// the messages.
		final SpringExt ext = SpringExtension.PROVIDER.get(context.system());
		final Props props = ext.props(EntityContextActor.class);
		actor = context.system().actorOf(props, EntityContextActor.NAME);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.zoneserver.entity.IEntityContext#sendMessage(net.bestia.
	 * messages.Message)
	 */
	@Override
	public void sendMessage(Message msg) {
		actor.tell(msg, ActorRef.noSender());
	}
}
