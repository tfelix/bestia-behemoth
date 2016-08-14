package net.bestia.zoneserver.actor.system;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.system.StartInitMessage;

/**
 * The {@link InitLocalActor} will only prepare the locally important data for
 * the server like preparing and loading scripts and other data types which must
 * be processed on a per server basis.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InitLocalActor extends UntypedActor {

	public static class LocalInitDone {
	};

	public static final String NAME = "initActor";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private boolean hasInitialized = false;

	@Override
	public void onReceive(Object message) throws Exception {

		if (message instanceof StartInitMessage) {
			if (hasInitialized) {
				return;
			}

			hasInitialized = true;

			// Start the initialization process.
			LOG.info("Start the local server initialization...");

			// Parse the scripts.
			
			// Stop ourselves. Work is done.
			sender().tell(new LocalInitDone(), getSelf());
			getContext().stop(getSelf());

		} else {
			unhandled(message);
		}

	}

	public static Props props() {
		return Props.create(InitLocalActor.class);
	}

}
