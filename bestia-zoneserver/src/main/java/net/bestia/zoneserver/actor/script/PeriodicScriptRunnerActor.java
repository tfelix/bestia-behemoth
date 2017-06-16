package net.bestia.zoneserver.actor.script;

import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.ScriptIntervalMessage;
import net.bestia.zoneserver.actor.BestiaPeriodicActor;
import net.bestia.zoneserver.actor.entity.EntityStatusTickActor;
import net.bestia.zoneserver.script.ScriptService;

/**
 * This actor is used to run periodically script function. TODO Wenn der
 * {@link EntityStatusTickActor} gut funktioniert den hier auch zu einem
 * BestiaPeriodicTerminatingActor machen.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class PeriodicScriptRunnerActor extends BestiaPeriodicActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final ScriptService scriptService;
	private long scriptId;

	public PeriodicScriptRunnerActor(ScriptService scriptService) {

		this.scriptService = Objects.requireNonNull(scriptService);
	}

	private void handleSetupMessage(ScriptIntervalMessage msg) {
		LOG.debug("Received perodic request for: {}.", msg);
		scriptId = msg.getScriptEntityId();
		startInterval(msg.getDelay());
	}

	@Override
	protected void onTick() {

		try {
			scriptService.triggerScriptIntervalCallback(scriptId);
		} catch (IllegalArgumentException e) {
			LOG.debug("Entity had no script callback component attached.", e);
			context().stop(getSelf());
		} catch (Exception e) {
			LOG.warning("Error during script interval execution. Stopping interval.", e);
			context().stop(getSelf());
		}
	}

	@Override
	protected void handleMessage(Object message) {
		if (message instanceof ScriptIntervalMessage) {
			handleSetupMessage((ScriptIntervalMessage) message);
		} else {
			unhandled(message);
		}
	}

}
