package net.bestia.zoneserver.actor.zone;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.system.StartInitMessage;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.SpringExtension.SpringExt;
import net.bestia.zoneserver.actor.map.LoadMapFileActor;

/**
 * Upon receiving the StartInit message the actor will start its work: Depending
 * on the given config it will generate a whole new world (by spawning the
 * needed worker actors chains) or it will restart the bestia service. This
 * means it will reload a given/saved map file and repopulate all the caches
 * with entity instances. If there is no data to be found it will default fall
 * back to a default world creation.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class InitGlobalActor extends UntypedActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private boolean hasInitialized = false;
	
	

	public InitGlobalActor() {

	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (message instanceof StartInitMessage) {
			if (hasInitialized) {
				return;
			}
			
			final SpringExt springExt = SpringExtension.Provider.get(getContext().system());

			hasInitialized = true;

			// Start the initialization process.
			LOG.info("Start the global server initialization...");

			// Load the sample map into the server cache.
			final ActorRef loadActor = getContext().actorOf(springExt.props(LoadMapFileActor.class));
			loadActor.tell(message, getSelf());

		} else {
			unhandled(message);
		}

	}

}
