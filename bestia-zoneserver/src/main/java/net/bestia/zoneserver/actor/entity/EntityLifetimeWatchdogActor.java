package net.bestia.zoneserver.actor.entity;

import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.ScriptIntervalMessage;
import net.bestia.zoneserver.actor.BestiaPeriodicActor;
import net.bestia.zoneserver.script.ScriptService;

/**
 * This watchdog will remove the script entity as soon as its lifetime counter
 * is over.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntityLifetimeWatchdogActor extends BestiaPeriodicActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final ScriptService scriptService;
	private long scriptEntityId;

	public EntityLifetimeWatchdogActor(ScriptService scriptService) {

		this.scriptService = Objects.requireNonNull(scriptService);
	}

	private void handleSetupMessage(ScriptIntervalMessage msg) {
		LOG.debug("Received perodic request for: {}.", msg);
		scriptEntityId = msg.getScriptEntityId();
		startInterval(msg.getDelay());
	}

	@Override
	protected void onTick() {
		LOG.debug("Terminating script entity: {}.", scriptEntityId);

		// TODO Das hier muss man testen ob man nicht auch entities entfernen
		// kann die nicht nur ein script sind.
		scriptService.deleteScriptEntity(scriptEntityId);
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
