package net.bestia.zoneserver.actor.entity.component;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Scheduler;
import net.bestia.entity.MovingEntityService;
import net.bestia.messages.internal.entity.EntityMoveInternalMessage;
import net.bestia.model.geometry.Point;
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
public class PeriodicMoveActor extends AbstractActor {

	private final static String TICK_MSG = "onTick";

	private Cancellable tick;

	private final MovingEntityService movingService;

	private long entityId;
	private final Queue<Point> path = new LinkedList<>();

	@Autowired
	public PeriodicMoveActor(
			List<Point> path,
			MovingEntityService movingService) {

		this.path.addAll(path);
		this.movingService = Objects.requireNonNull(movingService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.matchEquals(TICK_MSG, x -> {
					handleMoveTick();
				})
				.match(EntityMoveInternalMessage.class, this::handleMoveMessage)
				.build();
	}

	@Override
	public void preStart() {
		// Here comes the trick: after half the time consider the entity
		// moved/active
		// on the next tile.
		final int moveDelay = movingService.getMoveDelayMs(entityId, path.peek()) / 2;
		setupMoveTick(moveDelay);
	}

	// override postRestart so we don't call preStart and schedule a new message
	@Override
	public void postRestart(Throwable reason) {
		// no op.
	}

	@Override
	public void postStop() throws Exception {

		if (tick != null) {
			tick.cancel();
		}

	}

	private void handleMoveMessage(EntityMoveInternalMessage msg) {

		entityId = msg.getEntityId();

		path.clear();
		path.addAll(msg.getPath());

		final int moveDelay = movingService.getMoveDelayMs(entityId, path.peek());
		setupMoveTick(moveDelay);
	}

	private void handleMoveTick() {

		final Point nextPoint = path.poll();
		movingService.moveToPosition(entityId, nextPoint);

		// Path empty and can we terminate now?
		if (path.isEmpty()) {
			getContext().stop(getSelf());
		} else {
			// Here comes the trick: after half the time consider the entity
			// moved/active on the next tile.
			final int moveDelay = movingService.getMoveDelayMs(entityId, path.peek()) / 2;
			setupMoveTick(moveDelay);
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
