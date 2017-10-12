package net.bestia.zoneserver.actor.entity.component;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.MovingEntityService;
import net.bestia.entity.PlayerEntityService;
import net.bestia.messages.cluster.entity.EntityMoveMessage;
import net.bestia.messages.entity.EntityMoveRequestMessage;
import net.bestia.messages.entity.EntityPositionMessage;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.SpringExtension;

/**
 * Handle movement of an entity. It will announce the intended move path with
 * timing to all clients in sight so they can start to show the walk animation
 * and will perform the movement timer triggers so the unit does move from tile
 * to tile.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class MovementComponentActor extends AbstractActor {

	public final static String NAME = "moveComponent";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final MovingEntityService movingService;
	private final PlayerEntityService playerEntityService;

	private ActorRef periodicMoveActor;
	private final long entityId;

	@Autowired
	public MovementComponentActor(long entityId, MovingEntityService movingService, PlayerEntityService playerEntityService) {

		this.movingService = Objects.requireNonNull(movingService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.entityId = entityId;
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityMoveMessage.class, this::handleMoveInternal)
				.match(EntityMoveRequestMessage.class, this::handleMove)
				.match(EntityPositionMessage.class, this::handlePosition)
				.match(Terminated.class, this::handleTerminated)
				.build();
	}

	/**
	 * Sets entity directly to a fixed position movement.
	 */
	private void handlePosition(EntityPositionMessage msg) {
		// This message should be coming only from the internal system so we
		// need no security checks.
		movingService.moveToPosition(msg.getEntityId(), msg.getPosition());
	}

	private void handleMove(EntityMoveRequestMessage msg) {
		// We need to check if the user owns this entity.
		if(!playerEntityService.hasPlayerEntity(msg.getAccountId(), msg.getEntityId())) {
			// Player does not own this bestia so we abort.
			LOG.warning("Player does not own the entity to move: {}.", msg);
			return;
		}
		
		movePath(msg.getPath());
	}

	private void handleMoveInternal(EntityMoveMessage msg) {
		movePath(msg.getPath());
	}

	private void movePath(List<Point> path) {

		if (periodicMoveActor != null) {
			getContext().unwatch(periodicMoveActor);
			periodicMoveActor.tell(PoisonPill.getInstance(), getSelf());
		}

		periodicMoveActor = SpringExtension.unnamedActorOf(getContext(), PeriodicMoveActor.class, path, entityId);
		getContext().watch(periodicMoveActor);
	}

	/**
	 * Handle the termination of the periodic movement and remove the actor ref
	 * so we can start a new one.
	 */
	private void handleTerminated(Terminated term) {

		final ActorRef termActor = term.actor();
		if (periodicMoveActor.equals(termActor)) {
			periodicMoveActor = null;
		}
	}
}
