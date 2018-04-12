package net.bestia.zoneserver.actor.zone;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.client.ClientFromMessageEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Central influx point for web clients. The incoming messages are resend
 * towards the connection actors which manage the client connections.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class WebIngestActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String NAME = "ingest";

	private final ActorRef postmaster;

	@Autowired
	public WebIngestActor(ActorRef postmaster) {

		this.postmaster = Objects.requireNonNull(postmaster);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(ClientFromMessageEnvelope.class, msg -> {
			LOG.debug("Received message from web: {}", msg);
			postmaster.tell(msg, getSender());
		}).build();
	}
}
