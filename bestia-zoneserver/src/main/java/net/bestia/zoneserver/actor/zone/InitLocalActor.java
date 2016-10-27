package net.bestia.zoneserver.actor.zone;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.StartInitMessage;

/**
 * The {@link InitLocalActor} will only prepare the locally important data for
 * the server like preparing and loading scripts and other data types which must
 * be processed on a per server basis.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class InitLocalActor extends UntypedActor {

	public static class LocalInitDone {
	};

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	@Override
	public void onReceive(Object message) throws Exception {

		if (!(message instanceof StartInitMessage)) {
			unhandled(message);
			return;
		}

		// Start the initialization process.
		LOG.info("Starting local server initialization.");

		// Parse the scripts.

		// Stop ourselves. Work is done.
		sender().tell(new LocalInitDone(), getSelf());
		getContext().stop(getSelf());

	}
}
