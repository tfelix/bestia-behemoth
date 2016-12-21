package net.bestia.zoneserver.actor.entity;

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

	private Cancellable tick;

	private final MovingEntityService movingManager;
	private final EntityService entityService;

	private long entityId;
	private Queue<Point> path;

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
				// Strange no entity anymore. Stop.
				getContext().stop(getSelf());
				return;
			}

			e.setPosition(nextPoint.getX(), nextPoint.getY());

			// Path empty and can we terminate now?
			if (path.isEmpty()) {
				getContext().stop(getSelf());
			} else {
				final int moveDelay = getMoveDelay(path.peek(), e);
				
				if(moveDelay == -1) {
					// Strange no entity anymore. Stop.
					getContext().stop(getSelf());
					return;
				}
				
				setupMoveTick(moveDelay);
			}

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

			path.addAll(msg.getPath());
		} else {
			unhandled(message);
		}
	}

	@Override
	public void postStop() throws Exception {
		super.postStop();

		if(tick != null) {
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

	private void setupMoveTick(int delay) {

		final Scheduler shed = getContext().system().scheduler();
		tick = shed.scheduleOnce(Duration.create(delay, TimeUnit.MILLISECONDS),
				getSelf(), TICK_MSG, getContext().system().dispatcher(), null);
	}

}
