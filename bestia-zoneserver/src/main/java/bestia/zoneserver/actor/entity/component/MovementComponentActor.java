package bestia.zoneserver.actor.entity.component;

import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Scheduler;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import bestia.entity.EntityService;
import bestia.entity.component.MoveComponent;
import bestia.model.geometry.Point;
import bestia.zoneserver.entity.MovingService;
import scala.concurrent.duration.Duration;

/**
 * Handle movement of an entity. It will announce the intended move path with
 * timing to all clients in sight so they can start to show the walk animation
 * and will perform the movement timer triggers so the unit does move from tile
 * to tile.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class MovementComponentActor extends AbstractActor {
	
	private final static String TICK_MSG = "onTick";
	public final static String NAME = "moveComponent";
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private Cancellable tick;

	private final MovingService movingService;
	private final EntityService entityService;

	private final long entityId;

	@Autowired
	public MovementComponentActor(long entityId,
			MovingService movingService,
			EntityService entityService) {

		this.movingService = Objects.requireNonNull(movingService);
		this.entityId = entityId;
		this.entityService = entityService;
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
		
		final Optional<MoveComponent> optMc = entityService.getComponent(entityId, MoveComponent.class);
		
		if(optMc.isPresent()) {
			final Point nextPos = optMc.get().getPath().peek();
			final int moveDelay = movingService.getMoveDelayMs(entityId, nextPos) / 2;
			setupMoveTick(moveDelay);
		} else {
			getContext().stop(getSelf());
		}
	}

  @Override
	public void postStop() throws Exception {

		if (tick != null) {
			tick.cancel();
		}

		entityService.deleteComponent(entityId, MoveComponent.class);
	}
	
	/**
	 * Move the entity.
	 */
	private void handleMoveTick() {

		// TODO Diese logik evtl in den MovingService überführen?
		final Optional<MoveComponent> optMc = entityService.getComponent(entityId, MoveComponent.class);
		
		if(optMc.isPresent()) {
			final Queue<Point> path = optMc.get().getPath();
			final Point nextPoint = path.poll();
			
			movingService.moveToPosition(entityId, nextPoint);

			// Path empty and can we terminate now?
			if (path.isEmpty()) {
				getContext().stop(getSelf());
			} else {
				// Here comes the trick: after half the time consider the entity
				// moved/active on the next tile in the path.
				final int moveDelay = movingService.getMoveDelayMs(entityId, path.peek()) / 2;
				LOG.debug("MoveCompActor: moveTo: {}, nextMove: {} in: {} ms.", nextPoint, path.peek(), moveDelay);
				setupMoveTick(moveDelay);
				// Save the updated move component.
				entityService.updateComponent(optMc.get());
			}
		} else {
			getContext().stop(getSelf());
		}
	}
	
	/**
	 * Setup a new movement tick based on the delay. If the delay is negative we
	 * know that we can not move and thus end the movement and this actor.
	 * 
	 * @param delayMs
	 */
	private void setupMoveTick(int delayMs) {
		if (delayMs < 0) {
			getContext().stop(getSelf());
			return;
		}

		final Scheduler shed = getContext().system().scheduler();
		tick = shed.scheduleOnce(Duration.create(delayMs, TimeUnit.MILLISECONDS),
				getSelf(), TICK_MSG, getContext().dispatcher(), null);
	}
}
