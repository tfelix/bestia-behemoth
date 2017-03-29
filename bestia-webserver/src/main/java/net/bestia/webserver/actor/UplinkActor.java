package net.bestia.webserver.actor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.routing.FromConfig;
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope;

/**
 * The {@link UplinkActor} is responsible for routing the uplink messages to the
 * bestia cluster.
 * 
 * @author Thomas Felix
 *
 */
public class UplinkActor extends UntypedActor {

	public static final String NAME = "uplinkService";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private ActorRef workerRouter = getContext().actorOf(FromConfig.getInstance().props(Props.empty()), "uplinkRouter");

	@Override
	public void onReceive(Object msg) throws Throwable {
		
		LOG.debug("Sending to uplink router: {}", msg);
		workerRouter.tell(new ConsistentHashableEnvelope(msg, msg), getSender());
	}

}
