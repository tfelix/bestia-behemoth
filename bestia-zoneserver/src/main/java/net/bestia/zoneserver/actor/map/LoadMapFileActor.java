package net.bestia.zoneserver.actor.map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.system.StartInitMessage;
import net.bestia.zoneserver.generator.map.MapGenerator;
import net.bestia.zoneserver.generator.map.TmxMapGenerator;
import net.bestia.zoneserver.service.MapService;
import net.bestia.zoneserver.service.ServerStartupConfiguration;

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
	private final ServerStartupConfiguration serverConfig;
	
	@Autowired
	public LoadMapFileActor(MapService mapService, ServerStartupConfiguration serverConfig) {
		this.mapService = mapService;
		this.serverConfig = serverConfig;
	}
	

	@Override
	public void onReceive(Object message) throws Exception {

		if (!(message instanceof StartInitMessage)) {
			unhandled(message);
			return;
		}

		// Start the initialization process.
		final String mapfile = serverConfig.getMapfile();
		LOG.info("Loading the mapfile: {}", mapfile);

		// Load the sample map into the server cache.
		final MapGenerator tmxGenerator = new TmxMapGenerator(mapService, mapfile);
		tmxGenerator.generate();
	}

}
