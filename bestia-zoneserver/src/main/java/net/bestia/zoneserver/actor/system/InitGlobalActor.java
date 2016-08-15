package net.bestia.zoneserver.actor.system;

import java.util.Objects;

import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.system.StartInitMessage;
import net.bestia.server.BestiaActorContext;
import net.bestia.zoneserver.service.MapService;
import net.bestia.zoneserver.zone.map.Map;

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
			Map bestiaMap = new Map();

			final MapService mapService = ctx.getSpringContext().getBean(MapService.class);
			mapService.setMap(bestiaMap);

		} else {
			unhandled(message);
		}

	}

	public static Props props(BestiaActorContext ctx) {
		return Props.create(InitGlobalActor.class, ctx).withDeploy(Deploy.local());
	}

}
