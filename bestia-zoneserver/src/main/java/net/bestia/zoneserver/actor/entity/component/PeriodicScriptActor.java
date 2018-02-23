package net.bestia.zoneserver.actor.entity.component;

import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Scheduler;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.component.ScriptComponent;
import net.bestia.zoneserver.script.ScriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import scala.concurrent.duration.Duration;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * This actor is used to run a periodically script function.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class PeriodicScriptActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final static String TICK_MSG = "onTick";
	
	public final static String NAME ="periodicScript";

	private Cancellable tick;

	private final ScriptService scriptService;

	private final long entityId;
	private final String scriptUuid;

	@Autowired
	public PeriodicScriptActor(ScriptService scriptService, long entityId, long delay, String scriptUuid) {

		this.scriptService = Objects.requireNonNull(scriptService);

		this.entityId = entityId;
		this.scriptUuid = scriptUuid;
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ScriptComponent.ScriptCallback.class, this::handleDelayChange)
				.matchEquals(TICK_MSG, x -> {
					onTick();
				})
				.build();
	}

	@Override
	public void postStop() throws Exception {

		if (tick != null) {
			tick.cancel();
		}

	}

	/**
	 * Sometimes if might be needed to change the delay of the script.
	 * 
	 * @param msg
	 */
	private void handleDelayChange(ScriptComponent.ScriptCallback msg) {
		tick.cancel();
		setupMoveTick(msg.getIntervalMs());
	}

	private void onTick() {

		try {
			scriptService.callScriptIntervalCallback(entityId, scriptUuid);
		} catch (Exception e) {
			LOG.warning("Error during script interval execution. Stopping callback interval.", e);
			context().stop(getSelf());
		}
	}

	/**
	 * Setup a new movement tick based on the delay. If the delay is negative we
	 * know that we can not move and thus end the movement and this actor.
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
