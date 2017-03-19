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

	// This router is used both with lookup and deploy of routees. If you
	// have a router with only lookup of routees you can use Props.empty()
	// instead of Props.create(StatsWorker.class).
	private ActorRef workerRouter = getContext().actorOf(
			FromConfig.getInstance().props(Props.empty()),
			"uplinkRouter");

	@Override
	public void onReceive(Object msg) throws Throwable {
		LOG.info("Sending to uplink router.");
		workerRouter.tell(new ConsistentHashableEnvelope(msg, msg), getSender());

	}

}
