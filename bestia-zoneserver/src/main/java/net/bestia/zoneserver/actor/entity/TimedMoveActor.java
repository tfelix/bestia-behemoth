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
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.entity.traits.Moving;
import net.bestia.zoneserver.service.EntityService;
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

	private int waitLatch = 0;
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
			final Moving e = entityService.getEntity(entityId, Moving.class);

			if (e == null) {

				// We are currently in waiting mode for another job. We will
				// wait for a few seconds for a new path to arrive.
				if (waitLatch > 0) {
					waitLatch--;
					return;
				}

				// Strange no entity anymore. Stop.
				getContext().stop(getSelf());
				return;
			}

			e.setPosition(nextPoint.getX(), nextPoint.getY());
			entityService.save(e);

			// Path empty and can we terminate now?
			if (path.isEmpty()) {
				getContext().stop(getSelf());
			} else {
				final int moveDelay = getMoveDelay(path.peek(), e);
				setupMoveTick(moveDelay);
			}

		} else if (message.equals(STOP_MESSAGE)) {
			// Stop the current movement but dont end the actor. We will soon
			// receive another movement job.
			path.clear();
			waitLatch = 5;
			setupMoveTick(100);
		} else if (message instanceof EntityMoveMessage) {
			// We get the order to reuse this actor and stop the current
			// movement.
			final EntityMoveMessage msg = (EntityMoveMessage) message;

			entityId = msg.getEntityId();

			final Moving e = entityService.getEntity(entityId, Moving.class);

			// Check if the entity can move. If not stop.
			if (e == null) {
				getContext().stop(getSelf());
				return;
			}

			path.clear();
			path.addAll(msg.getPath());
			
			final int moveDelay = getMoveDelay(path.peek(), e);
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
	private int getMoveDelay(Point nextPos, Moving e) {
		final Point p = e.getPosition();
		final double d = nextPos.getDistance(p);
		float speed = e.getMovementSpeed();

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
