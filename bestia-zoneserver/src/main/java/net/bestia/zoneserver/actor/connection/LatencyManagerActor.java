package net.bestia.zoneserver.actor.connection;

import net.bestia.messages.Envelope;
import net.bestia.messages.client.PongMessage;
import net.bestia.zoneserver.actor.zone.ClientMessageDigestActor;
import net.bestia.zoneserver.client.LatencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Scope("prototype")
public class LatencyManagerActor extends ClientMessageDigestActor {

	public final static String NAME = "latency";

	private final LatencyService latencyService;

	@Autowired
	public LatencyManagerActor(LatencyService latencyService) {

		this.latencyService = Objects.requireNonNull(latencyService);
		redirectConfig.matchEnvelope(PongMessage.class, this::onPongMessage);
	}

	/**
	 * Updates the latency setting for the client who has send this latency reading.
	 * @param msg
	 */
	private void onPongMessage(PongMessage msg, Envelope envelope) {
		latencyService.addLatency(msg.getAccountId(), msg.getStart(), System.currentTimeMillis());
	}
}