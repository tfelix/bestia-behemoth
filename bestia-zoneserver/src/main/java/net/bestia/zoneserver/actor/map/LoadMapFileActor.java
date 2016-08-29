package net.bestia.zoneserver.actor.map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.system.LoadMapfileMessage;
import net.bestia.zoneserver.service.MapService;
import net.bestia.zoneserver.zone.map.generator.MapGenerator;
import net.bestia.zoneserver.zone.map.generator.TmxMapGenerator;

/**
 * This actor will load the mapfile from a TMX file and put it into the game
 * cache. Useful for debugging.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class LoadMapFileActor extends UntypedActor {

	public static final String NAME = "loadMapfile";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);


	private final MapService mapService;
	
	@Autowired
	public LoadMapFileActor(MapService mapService) {
		this.mapService = mapService;
	}
	

	@Override
	public void onReceive(Object message) throws Exception {

		if (!(message instanceof LoadMapfileMessage)) {
			unhandled(message);
			return;
		}

		// Start the initialization process.
		final String file = ((LoadMapfileMessage) message).getMapfile();

		// Start the initialization process.
		LOG.info("Loading the mapfile: {}", file);

		// Load the sample map into the server cache.
		MapGenerator tmxGenerator = new TmxMapGenerator(mapService, file);
		tmxGenerator.generate();
	}

}
