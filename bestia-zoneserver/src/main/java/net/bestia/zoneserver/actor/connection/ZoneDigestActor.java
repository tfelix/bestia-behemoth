package net.bestia.zoneserver.actor.connection;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

@Component
@Scope("prototype")
public class ZoneDigestActor extends AbstractActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.matchAny(x -> LOG.info("Client send: {}.", x))
				.build();
	}

}
