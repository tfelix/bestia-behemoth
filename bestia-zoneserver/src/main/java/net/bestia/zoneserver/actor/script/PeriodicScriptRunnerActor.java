package net.bestia.zoneserver.actor.script;

import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.ScriptIntervalMessage;
import net.bestia.zoneserver.actor.BestiaPeriodicActor;
import net.bestia.zoneserver.script.ScriptService;

/**
 * This actor is used to run periodically script function.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class PeriodicScriptRunnerActor extends BestiaPeriodicActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private ScriptService scriptService;
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
			scriptService.triggerScriptInterval(scriptId);
		} catch (Exception e) {
			LOG.warning("Error during script interval execution. Stopping interval.", e);
			context().stop(getSelf());
		}
	}

	@Override
	protected void handleMessage(Object message) throws Exception {
		if (message instanceof ScriptIntervalMessage) {
			handleSetupMessage((ScriptIntervalMessage) message);
		} else {
			unhandled(message);
		}
	}

}
