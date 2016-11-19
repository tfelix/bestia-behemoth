package net.bestia.webserver.actor;

import akka.actor.DeadLetter;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * Watches the dead letter count. If the count reaches a certain point (which
 * means the connection is unreliable) it will terminate the webserver and
 * notice the other subsystems about the potential problem.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class DeadLetterWatchActor extends UntypedActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private int deadCount = 10;

	public DeadLetterWatchActor() {

	}

	// subscribe to cluster changes
	@Override
	public void preStart() {
		getContext().system().eventStream().subscribe(getSelf(), DeadLetter.class);
	}

	// re-subscribe when restart
	@Override
	public void postStop() {
		getContext().system().eventStream().unsubscribe(getSelf());
	}

	@Override
	public void onReceive(Object msg) throws Throwable {
		if (deadCount < deadCount - 3) {
			LOG.warning("Received a dead letter.");
		} else {
			LOG.warning(String.format("Received a dead letter. Preparing kill switch in %d...", deadCount));
		}

		if (deadCount == 0) {
			LOG.warning("Try to rejoin cluster.");
			// cluster.leave(cluster.selfAddress());
		}
	}

}
