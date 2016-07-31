package net.bestia.zoneserver.actor.entity;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.zoneserver.service.MovingEntityManager;
import net.bestia.zoneserver.zone.generation.Point;
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

	private final Cancellable tick = getContext().system().scheduler().schedule(
			Duration.create(500, TimeUnit.MILLISECONDS),
			Duration.create(1, TimeUnit.SECONDS),
			getSelf(), TICK_MSG, getContext().dispatcher(), null);

	private MovingEntityManager movingManager;

	private long entityId;
	private Queue<Point> path;

	@Override
	public void onReceive(Object message) throws Exception {
		if (message.equals(TICK_MSG)) {

			final Point nextPoint = path.poll();

			// TODO Was it a visible entity? If yes update all nearby entities.

			// TODO Was it a collidable entity? Update its collision based on
			// new position.

			// Path empty and can we terminate now?
			if (path.isEmpty()) {
				getContext().stop(getSelf());
			}

		} else if (message instanceof EntityMoveMessage) {
			// We get the order to reuse this actor and stop the current
			// movement.
			final EntityMoveMessage msg = (EntityMoveMessage) message;
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

}
