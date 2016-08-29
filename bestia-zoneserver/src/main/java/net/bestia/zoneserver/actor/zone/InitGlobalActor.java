package net.bestia.zoneserver.actor.zone;

import java.util.Objects;

import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.system.LoadMapfileMessage;
import net.bestia.messages.system.StartInitMessage;
import net.bestia.server.BestiaActorContext;
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
public class InitGlobalActor extends UntypedActor {

	public static final String NAME = "initActor";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private boolean hasInitialized = false;
	private final BestiaActorContext ctx;

	public InitGlobalActor(BestiaActorContext ctx) {

		this.ctx = Objects.requireNonNull(ctx);
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (message instanceof StartInitMessage) {
			if (hasInitialized) {
				return;
			}

			hasInitialized = true;

			// Start the initialization process.
			LOG.info("Start the global server initialization...");

			// Load the sample map into the server cache.
			// @TODO Das hier austauschen gegen config?
			final String mapFile = "C:\\Users\\Thomas\\workspace\\14BES-bestia-behemoth\\src\\game-data\\map\\test-zone1\\test-zone1.tmx";
			ActorRef loadActor = getContext().actorOf(LoadMapFileActor.props(ctx));
			loadActor.tell(new LoadMapfileMessage(mapFile), getSelf());

		} else {
			unhandled(message);
		}

	}

	public static Props props(BestiaActorContext ctx) {
		return Props.create(InitGlobalActor.class, ctx).withDeploy(Deploy.local());
	}

}
