package net.bestia.zoneserver.actor.entity;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.Cancellable;
import akka.actor.Scheduler;
import net.bestia.messages.entity.EntityMoveInternalMessage;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.MovingEntityService;
import scala.concurrent.duration.Duration;

/**
 * Upon receiving of a move message which contains a movement path we will
 * lookup the movable entity and sets them to the new position.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class PeriodicMovementActor extends BestiaActor {

	private final static String TICK_MSG = "onTick";
	public static final String STOP_MESSAGE = "STOP";

	private Cancellable tick;

	private final MovingEntityService movingService;

	private long entityId;
	private final Queue<Point> path = new LinkedList<>();

	@Autowired
	public PeriodicMovementActor(
			EntityService entityService,
			MovingEntityService movingService) {

		this.movingService = Objects.requireNonNull(movingService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.matchEquals(TICK_MSG, x -> {
					handleTick();
				})
				.matchEquals(STOP_MESSAGE, x -> {
					handleStop();
				})
				.match(EntityMoveInternalMessage.class, this::handleMoveMessage)
				.build();
	}

	private void handleMoveMessage(EntityMoveInternalMessage msg) {

		entityId = msg.getEntityId();

		path.clear();
		path.addAll(msg.getPath());

		final int moveDelay = movingService.getMoveDelayMs(entityId, path.peek());
		setupMoveTick(moveDelay);
	}

	/**
	 * Stops the actor and finish the movement.
	 */
	private void handleStop() {
		// Stop the current movement but do not end the actor. We will soon
		// receive another movement job.
		path.clear();
		setupMoveTick(100);
		getContext().stop(getSelf());
	}

	private void handleTick() {
		final Point nextPoint = path.poll();
		movingService.moveToPosition(entityId, nextPoint);

		// Path empty and can we terminate now?
		if (path.isEmpty()) {
			handleStop();
		} else {
			final int moveDelay = movingService.getMoveDelayMs(entityId, path.peek());
			setupMoveTick(moveDelay);
		}
	}

	@Override
	public void postStop() throws Exception {
		super.postStop();

		if (tick != null) {
			tick.cancel();
		}
	}

	/**
	 * Setup a new movement tick based on the delay. If the delay is negative we
	 * know that we can not move and thus end the movement and this actor.
	 * 
	 * @param delay
	 */
	private void setupMoveTick(int delay) {
		if (delay < 0) {
			getContext().stop(getSelf());
			return;
		}

		final Scheduler shed = getContext().system().scheduler();
		tick = shed.scheduleOnce(Duration.create(delay, TimeUnit.MILLISECONDS),
				getSelf(), TICK_MSG, getContext().dispatcher(), null);
	}

}
