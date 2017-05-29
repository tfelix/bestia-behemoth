package net.bestia.zoneserver.actor.entity;

import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.ScriptIntervalMessage;
import net.bestia.zoneserver.actor.BestiaPeriodicActor;
import net.bestia.zoneserver.entity.EntityRecycler;

/**
 * This watchdog will remove the script entity as soon as its lifetime counter
 * is over. 
 * 
 * TODO Diese Aktoren von selbst terminieren wenn nach einer gewissen
 * Zeitspanne keine Aufgabe eingetroffen ist.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntityLifetimeWatchdogActor extends BestiaPeriodicActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final EntityRecycler recycler;
	private long entityId;

	public EntityLifetimeWatchdogActor(EntityRecycler recycler) {

		this.recycler = Objects.requireNonNull(recycler);
	}

	private void handleSetupMessage(ScriptIntervalMessage msg) {
		LOG.debug("Received perodic request for: {}.", msg);
		entityId = msg.getScriptEntityId();
		startInterval(msg.getDelay());
	}

	@Override
	protected void onTick() {
		LOG.debug("Terminating script entity: {}.", entityId);

		// TODO Das hier muss man testen ob man nicht auch entities entfernen
		// kann die nicht nur ein script sind.
		recycler.free(entityId);
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
