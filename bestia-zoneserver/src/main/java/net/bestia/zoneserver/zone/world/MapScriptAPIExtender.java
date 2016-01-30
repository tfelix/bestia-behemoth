package net.bestia.zoneserver.zone.world;

import com.artemis.World;
import com.artemis.WorldConfiguration;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.system.ScriptIntervalSystem;
import net.bestia.zoneserver.script.MapScriptAPI;
import net.bestia.zoneserver.script.MapScriptFactory;
import net.bestia.zoneserver.zone.Zone;
import net.bestia.zoneserver.zone.map.Map;

/**
 * Adds and {@link MapScriptAPI} object to the world. So it can later be used in
 * scripts to access functions of the ECS.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapScriptAPIExtender implements WorldExtend {

	private final MapScriptAPI api = new MapScriptAPI();

	@Override
	public void extend(World world, Map map, Zone zone) {

		api.initWorld(world);
	}

	@Override
	public void configure(WorldConfiguration worldConfig, Map map, CommandContext ctx, Zone zone) {

		api.initContext(ctx);
		worldConfig.register(api);

		final MapScriptFactory factory = new MapScriptFactory(zone.getName(), api);	
		worldConfig.register(factory);
		
		worldConfig.setSystem(new ScriptIntervalSystem());
	}

}
