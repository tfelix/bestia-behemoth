package net.bestia.zoneserver.actor.zone;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.sharding.ClusterSharding;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.JsonMessage;
import net.bestia.server.EntryActorNames;
import net.bestia.zoneserver.service.LatencyService;

/**
 * This actor sends the incoming message towards the registered cluster sharding
 * actor which holds the connection to a client.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class SendClientActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "sendToClient";

	private ActorRef clientConnection;
	private final LatencyService latencyService;

	@Autowired
	public SendClientActor(LatencyService latencyService) {
		this.latencyService = Objects.requireNonNull(latencyService);
	}

	@Override
	public void preStart() throws Exception {
		clientConnection = ClusterSharding.get(getContext().getSystem()).shardRegion(EntryActorNames.INSTANCE.getSHARD_CONNECTION());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(JsonMessage.class, this::handleSendClient)
				.build();
	}

	private void handleSendClient(JsonMessage msg) {

		LOG.debug("Sending to client: {}", msg);

		if (msg instanceof EntityComponentEnvelope) {
			// If its a component message include the client latency in the
			// message because the clients might need this for animation
			// critical data.
			final int latency = latencyService.getClientLatency(msg.getAccountId());
			clientConnection.tell(((EntityComponentEnvelope) msg).createNewInstance(msg.getAccountId(), latency), getSender());
		} else {
			clientConnection.tell(msg, getSender());
		}
	}
}
