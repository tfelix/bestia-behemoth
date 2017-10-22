package net.bestia.zoneserver.actor.zone;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * Central ingestion point for web clients.
 * @author Thomas
 *
 */
public class IngestActor extends AbstractActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	@Override
	public Receive createReceive() {
		return receiveBuilder().matchAny(s -> {
			LOG.debug("Received message from remote: {}", s);
			System.out.println("Received: " + s.toString());
		}).build();
	}

}
