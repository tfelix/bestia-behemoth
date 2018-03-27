package net.bestia.zoneserver.actor.map;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import de.tfelix.bestia.worldgen.MapNodeGenerator;
import de.tfelix.bestia.worldgen.description.MapDescription;
import de.tfelix.bestia.worldgen.io.MapGenDAO;
import de.tfelix.bestia.worldgen.io.MasterConnector;
import de.tfelix.bestia.worldgen.map.MapPart;
import de.tfelix.bestia.worldgen.message.WorkstateMessage;
import net.bestia.zoneserver.config.StaticConfig;
import net.bestia.zoneserver.configuration.MapGenConfiguration;
import net.bestia.zoneserver.map.MapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Scope("prototype")
public class MapGeneratorClientActor extends AbstractActor implements MasterConnector {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public final static String NAME = "mapGeneratorClient";

	private final MapGenConfiguration genConfig;

	private MapNodeGenerator nodeGenerator;
	private ActorRef master;

	private final StaticConfig config;
	private final MapGenDAO mapGenDao;
	private final MapService mapService;

	@Autowired
	public MapGeneratorClientActor(StaticConfig config, @Qualifier("localMapGenDao") MapGenDAO mapGenDao,
                                 MapGenConfiguration genConfig, MapService mapService) {

		this.genConfig = genConfig;

		this.mapGenDao = Objects.requireNonNull(mapGenDao);
		this.config = Objects.requireNonNull(config);
		this.mapService = Objects.requireNonNull(mapService);
	}

	@Override
	public void preStart() throws Exception {

		nodeGenerator = genConfig.mapNodeGenerator(config, this, mapGenDao, mapService);
	}
	
	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(MapDescription.class, m -> {
					master = getSender();
					createWorld(m);
				})
				.match(MapPart.class, this::consumeMapPart)
				.match(String.class, this::startWorkload)
				.build();
	}

	private void startWorkload(String label) {
		LOG.info("Starting workload '{}'.", label);
		nodeGenerator.startWorkload(label);
	}

	private void consumeMapPart(MapPart mapPart) {
		LOG.info("Received new part {}.", mapPart);
		nodeGenerator.consumeMapPart(mapPart);
	}

	private void createWorld(MapDescription desc) {
		LOG.info("Received new map description {}.", desc);
		nodeGenerator.consumeMapDescription(desc);
	}

	@Override
	public void sendMaster(WorkstateMessage workstateMessage) {
		master.tell(workstateMessage, getSelf());
	}

}
