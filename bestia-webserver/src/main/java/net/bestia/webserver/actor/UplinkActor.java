package net.bestia.webserver.actor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.routing.FromConfig;
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope;

public class UplinkActor extends UntypedActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	// This router is used both with lookup and deploy of routees. If you
	// have a router with only lookup of routees you can use Props.empty()
	// instead of Props.create(StatsWorker.class).
	ActorRef workerRouter = getContext().actorOf(
			FromConfig.getInstance().props(Props.empty()),
			"uplinkRouter");

	@Override
	public void preStart() throws Exception {
		// TODO Auto-generated method stub
		workerRouter.tell("Hello World", getSelf());
	}

	@Override
	public void onReceive(Object msg) throws Throwable {
		// TODO Auto-generated method stub
		LOG.info("Sending to uplink router.");
		workerRouter.tell(new ConsistentHashableEnvelope(msg, msg.hashCode()), getSelf());

	}

}
