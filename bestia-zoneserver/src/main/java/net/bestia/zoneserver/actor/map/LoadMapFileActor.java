package net.bestia.zoneserver.actor.map;

import java.util.Objects;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.system.LoadMapfileMessage;
import net.bestia.server.BestiaActorContext;
import net.bestia.zoneserver.service.MapService;
import net.bestia.zoneserver.zone.map.Map;

/**
 * This actor will load the mapfile from a TMX file and put it into the game
 * cache. Useful for debugging.
 * 
 * @author Thomas
 *
 */
public class LoadMapFileActor extends UntypedActor {

	public static final String NAME = "loadMapfile";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final BestiaActorContext ctx;

	public LoadMapFileActor(BestiaActorContext ctx) {

		this.ctx = Objects.requireNonNull(ctx);
	}
	
	public static Props props(BestiaActorContext ctx) {
		return Props.create(LoadMapFileActor.class, ctx);
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (!(message instanceof LoadMapfileMessage)) {
			unhandled(message);
			return;
		}

		// Start the initialization process.
		final MapService mapService = ctx.getSpringContext().getBean(MapService.class);

		final String file = ((LoadMapfileMessage) message).getMapfile();

		// Start the initialization process.
		LOG.info("Loading the mapfile: {}", file);

		// Load the sample map into the server cache.
		Map bestiaMap = new Map();


		mapService.setMap(bestiaMap);

	}

}
