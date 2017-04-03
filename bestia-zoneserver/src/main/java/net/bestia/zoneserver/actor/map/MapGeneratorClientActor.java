package net.bestia.zoneserver.actor.map;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import de.tfelix.bestia.worldgen.MapNodeGenerator;
import de.tfelix.bestia.worldgen.description.MapDescription;
import de.tfelix.bestia.worldgen.io.ClientCom;
import de.tfelix.bestia.worldgen.io.MapGenDAO;
import de.tfelix.bestia.worldgen.map.MapDataPart;
import de.tfelix.bestia.worldgen.map.MapPart;
import de.tfelix.bestia.worldgen.message.WorkstateMessage;
import de.tfelix.bestia.worldgen.random.NoiseVector;
import de.tfelix.bestia.worldgen.workload.Job;
import de.tfelix.bestia.worldgen.workload.MultiplyJob;
import de.tfelix.bestia.worldgen.workload.Workload;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.map.HazelcastMapGenDAO;
import net.bestia.zoneserver.map.MapGeneratorConstants;
import net.bestia.zoneserver.service.StaticConfigurationService;

@Component
@Scope("prototype")
public class MapGeneratorClientActor extends BestiaActor implements ClientCom {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public final static String NAME = "mapGeneratorClient";

	private final HazelcastMapGenDAO dao;
	private final StaticConfigurationService config;

	private MapNodeGenerator nodeGenerator;
	private ActorRef master;

	@Autowired
	public MapGeneratorClientActor(HazelcastMapGenDAO dao, StaticConfigurationService config) {

		this.dao = Objects.requireNonNull(dao);
		this.config = Objects.requireNonNull(config);
	}

	@Override
	public void preStart() throws Exception {
		nodeGenerator = new MapNodeGenerator(config.getServerName(), this, dao);

		Workload work = new Workload(MapGeneratorConstants.WORK_SCALE);
		work.addJob(new MultiplyJob(3500, MapGeneratorConstants.HEIGHT_MAP));
		nodeGenerator.addWorkload(work);

		work = new Workload(MapGeneratorConstants.WORK_GEN_TILES);
		work.addJob(new Job() {
			
			private final static double WATERLEVEL = 100;
			
			private int waterCount = 0;
			private int landCount = 0;
			
			@Override
			public void foreachNoiseVector(MapGenDAO dao, MapDataPart data, NoiseVector vec) {
				
				if(vec.getValueDouble(MapGeneratorConstants.HEIGHT_MAP) < WATERLEVEL) {
					// Water tile.
					vec.setValue(MapGeneratorConstants.TILE_MAP, 11);
					waterCount++;
				} else {
					// Land tile.
					vec.setValue(MapGeneratorConstants.TILE_MAP, 80);
					landCount++;
				}
			}
			
			@Override
			public void onFinish(MapGenDAO dao, MapDataPart data) {
				LOG.debug("Finished map part. Land tiles: {}, water tiles: {}", landCount, waterCount);
			}
			
			@Override
			public void onStart() {
				waterCount = 0;
				landCount = 0;
			}
		});
		work.addJob(new Job() {
			@Override
			public void foreachNoiseVector(MapGenDAO dao, MapDataPart data, NoiseVector vec) {
				// Now the tiles must be saved.
				LOG.info("Map wird gespeichert.");
			}
		});
		
		nodeGenerator.addWorkload(work);
	}

	@Override
	public void onReceive(Object msg) throws Throwable {

		if (msg instanceof MapDescription) {
			// If we are requested to generate a new map we save the sender.
			master = getSender();
			createWorld((MapDescription) msg);
		} else if (msg instanceof MapPart) {
			consumeMapPart((MapPart) msg);
		} else if (msg instanceof String) {
			startWorkload((String) msg);
		} else {
			unhandled(msg);
		}
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
