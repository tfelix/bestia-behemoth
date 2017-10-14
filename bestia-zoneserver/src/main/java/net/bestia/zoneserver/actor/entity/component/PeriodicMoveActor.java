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
	
	public final static String NAME ="periodicMove";

	private Cancellable tick;

	private final MovingEntityService movingService;

	private final long entityId;
	private final Queue<Point> path = new LinkedList<>();

	@Autowired
	public PeriodicMoveActor(
			long entityId,
			List<Point> path,
			MovingEntityService movingService) {

		this.path.addAll(path);
		this.movingService = Objects.requireNonNull(movingService);
		this.entityId = entityId;
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.matchEquals(TICK_MSG, x -> {
					handleMoveTick();
				})
				.build();
	}
	
	@Override
	public void preStart() throws Exception {
		
		final int moveDelay = movingService.getMoveDelayMs(entityId, path.peek()) / 2;
		setupMoveTick(moveDelay);
		
	}

	@Override
	public void postStop() throws Exception {

		if (tick != null) {
			tick.cancel();
		}

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
