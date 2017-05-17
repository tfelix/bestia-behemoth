package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.Cancellable;
import akka.actor.Scheduler;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityMoveInternalMessage;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.EntityServiceContext;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.service.MovingEntityService;
import scala.concurrent.duration.Duration;

/**
 * Upon receiving of a move message we will lookup the movable entity and sets
 * them to the new position.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class PeriodicMovementActor extends BestiaActor {

	private final static String TICK_MSG = "onTick";
	public static final String STOP_MESSAGE = "STOP";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private Cancellable tick;

	private final MovingEntityService movingService;
	private final EntityService entityService;

	private long entityId;
	private final Queue<Point> path = new LinkedList<>();

	@Autowired
	public PeriodicMovementActor(
			EntityService entityService,
			MovingEntityService movingService) {

		this.entityService = Objects.requireNonNull(entityService);
		this.movingService = Objects.requireNonNull(movingService);
	}

	@Override
	public void onReceive(Object message) throws Throwable {
		if (message.equals(TICK_MSG)) {

			handleTick();

		} else if (message.equals(STOP_MESSAGE)) {

			handleStop();

		} else if (message instanceof EntityMoveInternalMessage) {

			handleMoveMessage((EntityMoveInternalMessage) message);

		} else {
			
			unhandled(message);
		}
	}

	private void handleMoveMessage(EntityMoveInternalMessage msg) {

		entityId = msg.getEntityId();

		final PositionComponent pos = entityService.getComponent(entityId, PositionComponent.class)
				.orElseThrow(IllegalStateException::new);

		path.clear();
		path.addAll(msg.getPath());

		final int moveDelay = getMoveDelay(path.peek(), pos);
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

		pos.setPosition(nextPoint.getX(), nextPoint.getY());
		entityService.saveComponent(pos);

		// Path empty and can we terminate now?
		if (path.isEmpty()) {
			handleStop();
		} else {
			final int moveDelay = getMoveDelay(path.peek(), pos);
			setupMoveTick(moveDelay);
		}
	}

	@Override
	public void postStop() throws Exception {
		super.postStop();

		if (tick != null) {
			tick.cancel();
		}

		movingService.removeMovingActorRef(entityId);
	}

	/**
	 * Calculates the next movement tick depending on the move speed. If -1 is
	 * returned this means that the unit can no longer move (an error occurred
	 * while calculating the movement).
	 * 
	 * @param nextPos
	 *            The next position.
	 * @param e
	 *            The moving entity.
	 * @return The delay in ms for the next movement tick, or -1 if an error has
	 *         occurred.
	 */
	private int getMoveDelay(Point nextPos, PositionComponent pos) {
		final Point p = pos.getPosition();
		final double d = nextPos.getDistance(p);
		float speed = 1;

		if (speed == 0) {
			return -1;
		}

		// Distance should be 1 or 2 (means walking diagonally)
		if (d > 1) {
			speed *= 1.31;
		}

		return (int) Math.floor((1 / 1.4) / speed * 1000);
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
