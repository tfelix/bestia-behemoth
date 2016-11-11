package net.bestia.zoneserver.actor.map;

import net.bestia.model.shape.Size;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.generator.map.MapSizeCalculator;

public class MapGeneratorActor extends BestiaActor {

	@Override
	public void onReceive(Object msg) throws Throwable {
		// TODO Auto-generated method stub
		
	}
	
	private void generateMap() {
		
		final int averageUser = 10;
		final Size mapSize = MapSizeCalculator.getSize(averageUser);
		
	}

}
