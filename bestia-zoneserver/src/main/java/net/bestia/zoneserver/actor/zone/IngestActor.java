package net.bestia.zoneserver.actor.zone;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.ClientMessageWrapper;

/**
 * Central ingestion point for web clients. The incoming messages are wrapped
 * and send to the parent actor.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class IngestActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String NAME = "ingest";

	public IngestActor() {

	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().matchAny(s -> {
			// Redirect the incoming messages wrapped to the parent.
			final ClientMessageWrapper wrapper = new ClientMessageWrapper(s);
			LOG.debug("Received message from remote: {}", s);
			getContext().parent().tell(wrapper, getSender());
		}).build();
	}

}
