package net.bestia.webserver.actor;

import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class DeadLetterWatchActor extends UntypedActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private int deadCount = 10;
	private Cluster cluster;
	
	public DeadLetterWatchActor() {
		

	}

	@Override
	public void onReceive(Object msg) throws Throwable {
		if(deadCount > 3) {
			LOG.warning("Received a dead letter.");
		} else {
			LOG.warning(String.format("Received a dead letter. Preparing kill switch in %d...", deadCount));
		}
		
		if(deadCount == 0) {
			LOG.warning("Try to rejoin cluster.");
			//cluster.leave(cluster.selfAddress());
		}
	}

}
