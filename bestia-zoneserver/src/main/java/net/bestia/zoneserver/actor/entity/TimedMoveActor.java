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
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.service.MovingEntityService;
import scala.concurrent.duration.Duration;

/**
 * When the entity will receive a walk request message with a path we will spawn
 * up a TimedMoveActor which will periodically wake up and move a given entity
 * to the desired location.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class TimedMoveActor extends BestiaActor {

	private final static String TICK_MSG = "tick";

	public static final String STOP_MESSAGE = "STOP";

	private Cancellable tick;

	private final MovingEntityService movingManager;
	private final EntityService entityService;

	private long entityId;
	private final Queue<Point> path = new LinkedList<>();

	@Autowired
	public TimedMoveActor(
			EntityService entityService,
			MovingEntityService movingManager) {

		this.entityService = Objects.requireNonNull(entityService);
		this.movingManager = Objects.requireNonNull(movingManager);
	}

	@Override
	public void onReceive(Object message) throws Exception {
		if (message.equals(TICK_MSG)) {

			final Point nextPoint = path.poll();
			final Entity entity = entityService.getEntity(entityId);
			final PositionComponent pos = entityService.getComponent(entity, PositionComponent.class)
					.orElseThrow(IllegalStateException::new);

			// TODO Das hier klüger mit nächster position an den client senden.
			pos.setPosition(nextPoint.getX(), nextPoint.getY());
			entityService.save(entity);

			// Path empty and can we terminate now?
			if (path.isEmpty()) {
				getContext().stop(getSelf());
			} else {
				final int moveDelay = getMoveDelay(path.peek(), pos);
				setupMoveTick(moveDelay);
			}

		} else if (message.equals(STOP_MESSAGE)) {
			// Stop the current movement but dont end the actor. We will soon
			// receive another movement job.
			path.clear();
			setupMoveTick(100);
		} else if (message instanceof EntityMoveInternalMessage) {
			// We get the order to reuse this actor and stop the current
			// movement.
			final EntityMoveInternalMessage msg = (EntityMoveInternalMessage) message;

			entityId = msg.getEntityId();

			final PositionComponent pos = entityService.getComponent(entityId, PositionComponent.class)
					.orElseThrow(IllegalStateException::new);

			path.clear();
			path.addAll(msg.getPath());

			final int moveDelay = getMoveDelay(path.peek(), pos);
			setupMoveTick(moveDelay);
		} else {
			unhandled(message);
		}
	}

	@Override
	public void postStop() throws Exception {
		super.postStop();

		if (tick != null) {
			tick.cancel();
		}

		movingManager.removeMovingActorRef(entityId);
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
