package net.bestia.zoneserver.actor.entity;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.Cancellable;
import akka.actor.Scheduler;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.configuration.CacheConfiguration;
import net.bestia.zoneserver.entity.LivingEntity;
import net.bestia.zoneserver.entity.traits.Moving;
import net.bestia.zoneserver.service.CacheManager;
import net.bestia.zoneserver.service.MovingEntityManager;
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

	private final MovingEntityManager movingManager;
	private final CacheManager<Long, LivingEntity> entityCache;

	private long entityId;
	private Queue<Point> path;

	@Autowired
	public TimedMoveActor(
			@Qualifier(CacheConfiguration.ACTIVE_BESTIA_CACHE) CacheManager<Long, LivingEntity> entityCache,
			MovingEntityManager movingManager) {

		this.entityCache = Objects.requireNonNull(entityCache);
		this.movingManager = Objects.requireNonNull(movingManager);
	}

	@Override
	public void onReceive(Object message) throws Exception {
		if (message.equals(TICK_MSG)) {

			final Point nextPoint = path.poll();
			final Moving e = (Moving) entityCache.get(entityId);

			if (e == null) {
				// Strange no entity anymore. Stop.
				getContext().stop(getSelf());
				return;
			}

			e.setPosition(nextPoint.getX(), nextPoint.getY());

			// TODO Was it a visible entity? If yes update all nearby entities.

			// TODO Was it a collidable entity? Update its collision based on
			// new position.

			// Path empty and can we terminate now?
			if (path.isEmpty()) {
				getContext().stop(getSelf());
			} else {
				final int moveDelay = getMoveDelay(path.peek(), e);
				setupMoveTick(moveDelay);
			}

		} else if (message instanceof EntityMoveMessage) {
			// We get the order to reuse this actor and stop the current
			// movement.
			final EntityMoveMessage msg = (EntityMoveMessage) message;

			entityId = msg.getEntityId();

			final LivingEntity e = entityCache.get(entityId);

			// Check if the entity can move. If not stop.
			if (e == null || !(e instanceof Moving)) {
				getContext().stop(getSelf());
				return;
			}

			final List<Point> path = msg.getPath()
					.stream()
					.map(p -> new Point(p.getX(), p.getY()))
					.collect(Collectors.toList());

			path.clear();
			path.addAll(path);
		} else {
			unhandled(message);
		}
	}

	@Override
	public void postStop() throws Exception {
		super.postStop();

		tick.cancel();
		movingManager.removeMovingActorRef(entityId);
	}

	private int getMoveDelay(Point nextPos, Moving e) {
		final Point p = e.getPosition();
		final long d = Math.abs(p.getX() - nextPos.getX()) + Math.abs(p.getY() - nextPos.getY());

		float speed = e.getMovementSpeed();

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
