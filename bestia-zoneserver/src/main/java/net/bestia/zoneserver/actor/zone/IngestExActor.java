package net.bestia.zoneserver.actor.zone;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.misc.PongMessage;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.connection.ConnectionManagerActor;

/**
 * The ingestion extended actor is a development actor to help the transition
 * towards a cleaner actor massaging managament. It serves as a proxi
 * re-directing the incoming messages towards the new system or to the legacy
 * system.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class IngestExActor extends AbstractActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String NAME = "ingestEx";

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(PongMessage.class, this::redirectConnection)
				.match(ClientConnectionStatusMessage.class, this::redirectConnection)
				.matchAny(this::redirectLegacy)
				.build();
	}

	/**
	 * Redirect all other messages to the legacy actor.
	 * 
	 * @param msg
	 */
	private void redirectLegacy(Object msg) {
		LOG.debug("IngestEx legacy: {}.", msg);
		AkkaSender.sendToActor(getContext(), IngestActor.NAME, msg, getSender());
	}

	private void redirectConnection(Object msg) {
		LOG.debug("IngestEx received: {}.", msg);
		
		AkkaSender.sendToActor(getContext(), ConnectionManagerActor.NAME, msg, getSender());
		redirectLegacy(msg);
	}
}
