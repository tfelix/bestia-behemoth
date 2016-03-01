package net.bestia.zoneserver.zone.world;

import java.util.List;

import com.artemis.World;
import com.artemis.WorldConfiguration;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.manager.MobSpawnManager;
import net.bestia.zoneserver.ecs.system.MobSpawnSystem;
import net.bestia.zoneserver.zone.Zone;
import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.spawn.Spawner;

/**
 * The {@link MobSpawnExtender} will read the lines of the map file and create
 * entities which handle the creation and spawning of entities.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MobSpawnExtender implements WorldExtend {
	
	private MobSpawnManager manager;

	@Override
	public void extend(World world, Map map, Zone zone) {

		manager.spawnAll();
	}

	@Override
	public void configure(WorldConfiguration worldConfig, Map map, CommandContext ctx, Zone zone) {

		// Create the SpawnLocations from the map. Keeps track of active bestias
		// and sets timeouts to re-spawn them.
		final List<Spawner> spawns = map.getSpawnlist();
		manager = new MobSpawnManager(spawns);
		worldConfig.setSystem(manager);

		// The MobSpawnSystem actually spawns entities.
		worldConfig.setSystem(new MobSpawnSystem());
	}

}
