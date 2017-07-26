package net.bestia.zoneserver.actor.script;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.script.ScriptIntervalMessage;
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

	private final ScriptService scriptService;
	private long scriptId;

	@Autowired
	public PeriodicScriptRunnerActor(ScriptService scriptService, ScriptIntervalMessage msg) {

		this.scriptService = Objects.requireNonNull(scriptService);
		
		LOG.debug("Received perodic request for: {}.", msg);
		
		scriptId = msg.getScriptEntityId();
		startInterval(msg.getDelay());
	}

	@Override
	protected void onTick() {

		try {
			scriptService.callScriptIntervalCallback(scriptId);
		} catch (IllegalArgumentException e) {
			LOG.debug("Error while script execution Stopping callback interval.", e);
			context().stop(getSelf());
		} catch (Exception e) {
			LOG.warning("Error during script interval execution. Stopping callback interval.", e);
			context().stop(getSelf());
		}
	}

	@Override
	protected void handleMessage(Object message) {
		unhandled(message);
	}

}
