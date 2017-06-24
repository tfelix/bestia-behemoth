package net.bestia.zoneserver.actor.entity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.messages.internal.entity.EntityDeleteInternalMessage;
import net.bestia.messages.internal.entity.EntityMoveInternalMessage;
import net.bestia.messages.internal.entity.EntityRegenTickMessage;
import net.bestia.messages.internal.script.ScriptIntervalMessage;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.script.PeriodicScriptRunnerActor;

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
@Component
@Scope("prototype")
public class EntityActor extends BestiaActor {
	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

	private final static String ACTOR_NAME = "entity-%d";
	private final long entityId;

	private ActorRef movementActor;
	private ActorRef regenerationTickActor;
	private ActorRef scriptTickActor;

	@Autowired
	public EntityActor(long entityId) {

		this.entityId = entityId;
	}

	public static String getActorName(long entityId) {
		return String.format(ACTOR_NAME, entityId);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ScriptIntervalMessage.class, this::handleScriptIntervalMessage)
				.match(EntityMoveMessage.class, this::handleClientMoveMessage)
				.match(EntityMoveInternalMessage.class, this::handleInternalMoveMessage)
				.match(EntityDeleteInternalMessage.class, this::handleDeleteMessage)
				.match(EntityRegenTickMessage.class, this::handleRegenerationTickMessage)
				.match(Terminated.class, this::handleTerminated)
				.build();
	}

	private void handleRegenerationTickMessage(EntityRegenTickMessage msg) {

		stopRegenTick();

		if (msg.isActive()) {
			regenerationTickActor = SpringExtension.unnamedActorOf(getContext(),
					EntityStatusTickActor.class,
					msg.getEntityId());
		}
	}

	/**
	 * Check which actor was terminated and remove it.
	 * 
	 * @param term
	 *            The termination message from akka.
	 */
	private void handleTerminated(Terminated term) {

		final ActorRef termActor = term.actor();

		if (termActor.equals(movementActor)) {

			LOG.debug("Movement actor has terminated.");
			movementActor = null;

		} else if (termActor.equals(scriptTickActor)) {

			LOG.debug("Script trigger actor has terminated.");
			scriptTickActor = null;

		} else if (termActor.equals(regenerationTickActor)) {

			LOG.debug("Regeneration actor has terminated.");
			regenerationTickActor = null;

		} else {

			LOG.warning("Unknown actor {} has terminated. Cant handle event.",
					term.getActor().path().toStringWithoutAddress());

		}

	}

	private void handleDeleteMessage(EntityDeleteInternalMessage msg) {
		stopAll();
		SpringExtension.unnamedActorOf(getContext(), EntityDeleteActor.class, msg.getEntityId());
	}

	/**
	 * Setup a periodic script runner actor.
	 */
	private void handleScriptIntervalMessage(ScriptIntervalMessage msg) {

		stopScript();

		scriptTickActor = SpringExtension.unnamedActorOf(context(), PeriodicScriptRunnerActor.class, msg);
		context().watch(scriptTickActor);

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

	private void stopScript() {
		if (scriptTickActor != null) {
			context().stop(scriptTickActor);
			context().unwatch(scriptTickActor);
			scriptTickActor = null;
		}
	}

	private void stopMovement() {
		if (movementActor != null) {
			context().stop(movementActor);
			context().unwatch(movementActor);
			movementActor = null;
		}
	}

	private void stopRegenTick() {
		if (regenerationTickActor != null) {
			context().stop(regenerationTickActor);
			context().unwatch(regenerationTickActor);
			regenerationTickActor = null;
		}
	}

	/**
	 * Stops the actor and all child actors.
	 */
	private void stopAll() {
		stopMovement();
		stopRegenTick();
	}
}
