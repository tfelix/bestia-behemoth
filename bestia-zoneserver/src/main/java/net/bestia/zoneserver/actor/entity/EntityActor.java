package net.bestia.zoneserver.actor.entity;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.messages.internal.entity.EntityDeleteInternalMessage;
import net.bestia.messages.internal.entity.EntityMoveInternalMessage;
import net.bestia.messages.internal.script.ScriptIntervalMessage;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.actor.SpringExtension;

/**
 * The {@link EntityActor} is a persistent actor managing all aspects of a
 * spawned entity. This means it will keep references to AI actors or attached
 * script actors. This actor has to be used as an persisted shared actor.
 * 
 * This actor has to react on certain incoming request messages like for example
 * attaching a ticking script to the entity.
 * 
 * @author Thomas Felix
 *
 */
public class EntityActor extends BestiaActor {
	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

	private final static String PERSISTED_NAME = "entity-%d";
	private final long entityId;

	private ActorRef movementActor;

	public EntityActor(long entityId) {

		this.entityId = entityId;
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ScriptIntervalMessage.class, this::handleScriptIntervalMessage)
				.match(EntityMoveMessage.class, this::handleClientMoveMessage)
				.match(EntityMoveInternalMessage.class, this::handleInternalMoveMessage)
				.match(EntityDeleteInternalMessage.class, this::handleDeleteMessage)
				.match(Terminated.class, this::handleTerminated)
				.build();
	}

	private void handleTerminated(Terminated term) {

		if (term.actor().equals(movementActor)) {
			
			LOG.debug("Movement actor has terminated.");
			movementActor = null;
			
		} else {
			
			LOG.warning("Unknown actor {} has terminated. Cant handle event.",
					term.getActor().path().toStringWithoutAddress());
			
		}

	}

	private void handleDeleteMessage(EntityDeleteInternalMessage msg) {

	}

	/**
	 * Setup a periodic script runner actor.
	 */
	private void handleScriptIntervalMessage(ScriptIntervalMessage msg) {

	}

	/**
	 * Transforms msg to internal movement message.
	 */
	private void handleClientMoveMessage(EntityMoveMessage msg) {
		handleInternalMoveMessage(new EntityMoveInternalMessage(entityId, msg.getPath()));
	}

	/**
	 * Setup a moving actor to perform the periodic movement.
	 */
	private void handleInternalMoveMessage(EntityMoveInternalMessage msg) {

		stopMovement();

		movementActor = SpringExtension.actorOf(context(), EntityMovementActor.class);
		context().watch(movementActor);
		movementActor.tell(msg, getSelf());
	}
	
	private void stopMovement() {
		if (movementActor != null) {
			context().stop(movementActor);
			context().unwatch(movementActor);
			movementActor = null;
		}
	}

	/**
	 * Stops the actor and all child actors.
	 */
	private void stopAll() {
		stopMovement();
	}
}
