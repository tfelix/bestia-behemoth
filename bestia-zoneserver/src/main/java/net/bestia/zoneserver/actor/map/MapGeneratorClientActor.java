package net.bestia.zoneserver.actor.map;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import de.tfelix.bestia.worldgen.MapNodeGenerator;
import de.tfelix.bestia.worldgen.io.ClientCom;
import de.tfelix.bestia.worldgen.message.WorkstateMessage;
import de.tfelix.bestia.worldgen.workload.Workload;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.dao.HazelcastMapGenDAO;
import net.bestia.zoneserver.map.MapGeneratorConstants;
import net.bestia.zoneserver.service.StaticConfigurationService;

@Component
@Scope("prototype")
public class MapGeneratorClientActor extends BestiaActor implements ClientCom {
	
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
	}
	

	@Override
	public void onReceive(Object msg) throws Throwable {
		
		// If we are requested to generate a new map we save the sender.
		master = getSender();
		
	}
	
	private void generateMap() {
		
		Workload builder = new Workload(MapGeneratorConstants.WORK_SCALE);
		//builder.addJob(new Job()..builder.);
		
		//nodeGenerator.addWorkload(builder.build());
		//nodeGenerator.addWorkload(builder.build());
		//nodeGenerator.addWorkload(builder.build());
	}

	@Override
	public void sendMaster(WorkstateMessage workstateMessage) {
		master.tell(workstateMessage, getSelf());
	}

}
