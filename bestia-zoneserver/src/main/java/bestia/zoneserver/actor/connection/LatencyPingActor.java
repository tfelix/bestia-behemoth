package bestia.zoneserver.actor.connection;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import bestia.messages.client.PingMessage;
import bestia.zoneserver.service.LatencyService;
import scala.concurrent.duration.Duration;

/**
 * Periodically pings the client to receive the pong request.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class LatencyPingActor extends AbstractActor {

	private final Cancellable latencyTick = getContext().getSystem().scheduler().schedule(
			Duration.create(2, TimeUnit.SECONDS),
			Duration.create(5, TimeUnit.SECONDS),
			getSelf(), LATENCY_REQUEST_MSG, getContext().dispatcher(), null);

	public final static String NAME = "ping";
	private final static String LATENCY_REQUEST_MSG = "latency";
	private final static int CLIENT_TIMEOUT_MS = 30 * 1000;

	private final ActorRef clientConnection;
	private final LatencyService latencyService;
	private final long accountId;


	@Autowired
	public LatencyPingActor(LatencyService latencyService, Long accountId, ActorRef clientConnection) {

		this.accountId = accountId;
		this.clientConnection = Objects.requireNonNull(clientConnection);
		this.latencyService = Objects.requireNonNull(latencyService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.matchEquals(LATENCY_REQUEST_MSG, msg -> onLatencyRequest())
				.build();
	}
	
	@Override
	public void preStart() throws Exception {
		final long now = System.currentTimeMillis();
		latencyService.addLatency(accountId, now, now + 10);
	}

	@Override
	public void postStop() throws Exception {
		latencyTick.cancel();
		latencyService.delete(accountId);
	}

	/**
	 * The client is send a latency request message.
	 */
	private void onLatencyRequest() {

		// Check how many latency requests we have missed.
		long lastReply = latencyService.getLastClientReply(accountId);
		long dLastReply = System.currentTimeMillis() - lastReply;

		if (lastReply > 0 && dLastReply > CLIENT_TIMEOUT_MS) {
			// Connection seems to have dropped. Signal the server that the
			// client has disconnected and terminate.
			getContext().parent().tell(PoisonPill.getInstance(), getSelf());
		} else {
			final PingMessage ping = new PingMessage(accountId);
			clientConnection.tell(ping, getSelf());
		}
	}
}
