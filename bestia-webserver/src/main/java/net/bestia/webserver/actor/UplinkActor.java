package net.bestia.webserver.actor;

import akka.actor.UntypedActor;
import akka.routing.FromConfig;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.TypedActor.TypedActor;

public class UplinkActor extends UntypedActor {

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
		
		workerRouter.tell("Hello World", getSelf());

	}

}
