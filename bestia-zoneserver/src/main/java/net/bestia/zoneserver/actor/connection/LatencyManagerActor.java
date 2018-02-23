package net.bestia.zoneserver.actor.connection;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import net.bestia.messages.client.PongMessage;
import net.bestia.zoneserver.actor.zone.ClientMessageActor.RedirectMessage;
import net.bestia.zoneserver.service.LatencyService;

/**
 * This actor holds the connection details of a client and is able to redirect
 * messages towards this client. It keeps track of the latency checks and
 * possibly disconnects the client if it does not reply in time.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class LatencyManagerActor extends AbstractActor {

	public final static String NAME = "latency";

	private final LatencyService latencyService;

	@Autowired
	public LatencyManagerActor(LatencyService latencyService) {

		this.latencyService = Objects.requireNonNull(latencyService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(PongMessage.class, this::onPongMessage)
				.build();
	}
	
	@Override
	public void preStart() throws Exception {
		RedirectMessage msg = RedirectMessage.get(PongMessage.class);
		context().parent().tell(msg, getSelf());
	}

	/**
	 * Updates the latency setting for the client who has send this latency reading.
	 * @param msg
	 */
	private void onPongMessage(PongMessage msg) {		
		latencyService.addLatency(msg.getAccountId(), msg.getStart(), System.currentTimeMillis());
	}
}
