package net.bestia.zoneserver.actor.zone;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * This is a cluster singelton actor. It centralized the control over the whole
 * bestia cluster. Upon receiving control messages it performs centralized
 * orchestration like generating a new map or sending commands to server
 * instances.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ClusterControlActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String START_MSG = "init.start";

	private boolean hasInitialized = false;


	public ClusterControlActor() {
		// no op.
	}

	@Override
	public void preStart() throws Exception {
		LOG.warning("INITGLOBAL STARTED");
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.matchEquals(START_MSG, m -> this.startInit())
				.build();
	}

	private void startInit() {

		if (hasInitialized) {
			return;
		}

		hasInitialized = true;

		// Start the initialization process.
		LOG.info("Start the global server initialization...");
	}
}
