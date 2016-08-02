package net.bestia.zoneserver.actor.entity;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.Scheduler;
import akka.actor.UntypedActor;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.model.zone.Point;
import net.bestia.server.BestiaActorContext;
import net.bestia.zoneserver.component.CachesConfiguration;
import net.bestia.zoneserver.service.CacheManager;
import net.bestia.zoneserver.service.MovingEntityManager;
import net.bestia.zoneserver.zone.Position;
import net.bestia.zoneserver.zone.entity.Entity;
import net.bestia.zoneserver.zone.entity.Moving;
import scala.concurrent.duration.Duration;

/**
 * When the entity will receive a walk request message with a path we will spawn
 * up a TimedMoveActor which will periodically wake up and move a given entity
 * to the desired location.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TimedMoveActor extends UntypedActor {

	private final static String TICK_MSG = "tick";

	private Cancellable tick;

	private final MovingEntityManager movingManager;
	private final CacheManager<Long, Entity> entityCache;

	private long entityId;
	private Queue<Point> path;

	@SuppressWarnings("unchecked")
	public TimedMoveActor(BestiaActorContext ctx) {

		this.entityCache = ctx.getSpringContext().getBean(CachesConfiguration.ENTITY_CACHE, CacheManager.class);
		this.movingManager = ctx.getSpringContext().getBean(MovingEntityManager.class);
	}

	public static Props props(final BestiaActorContext ctx) {
		// Props must be deployed locally since we contain a non serializable
		return Props.create(TimedMoveActor.class, ctx);
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

			final Entity e = entityCache.get(entityId);

			// Check if the entity can move. If not stop.
			if (e == null || !(e instanceof Moving)) {
				getContext().stop(getSelf());
				return;
			}

			final List<Point> path = msg.getPath()
					.stream()
					.map(p -> new Point(p.x, p.y))
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
		final Position p = e.getPosition();
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
