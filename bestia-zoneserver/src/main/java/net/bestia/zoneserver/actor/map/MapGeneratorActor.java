package net.bestia.zoneserver.actor.map;

import de.tfelix.bestia.worldgen.MapNodeGenerator;
import de.tfelix.bestia.worldgen.io.ClientCom;
import de.tfelix.bestia.worldgen.message.WorkstateMessage;
import de.tfelix.bestia.worldgen.workload.Workload;
import net.bestia.model.geometry.Size;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.dao.HazelcastMapGenDAO;
import net.bestia.zoneserver.map.MapGeneratorConstants;

public class MapGeneratorActor extends BestiaActor implements ClientCom {
	
	public final static String NAME = "mapGeneratorClient";
	
	private MapNodeGenerator nodeGenerator;

	@Override
	public void onReceive(Object msg) throws Throwable {
		// TODO Auto-generated method stub
		
	}
	
	private void generateMap() {
		
		// TEMP
		
		String nodeName = "test";
		HazelcastMapGenDAO dao = null;
		
		nodeGenerator = new MapNodeGenerator(nodeName, this, dao);
		
		Workload builder = new Workload(MapGeneratorConstants.WORK_SCALE);
		//builder.addJob(new Job()..builder.);
		
		nodeGenerator.addWorkload(builder.build());
		nodeGenerator.addWorkload(builder.build());
		nodeGenerator.addWorkload(builder.build());
	}

	@Override
	public void sendMaster(WorkstateMessage workstateMessage) {
		// TODO Auto-generated method stub
		
	}

}
