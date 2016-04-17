package net.bestia.zoneserver.zone.world;

import com.artemis.World;
import com.artemis.WorldConfiguration;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.entity.EcsEntityFactory;
import net.bestia.zoneserver.ecs.system.ScriptCallableSystem;
import net.bestia.zoneserver.script.ScriptApi;
import net.bestia.zoneserver.zone.Zone;
import net.bestia.zoneserver.zone.map.Map;

/**
 * Adds and {@link ScriptApi} object to the world. So it can later be used in
 * scripts to access functions of the ECS.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapScriptAPIExtender implements WorldExtend {

	private final ScriptApi api = new ScriptApi();

	@Override
	public void extend(World world, Map map, Zone zone) {

		api.initWorld(world, new EcsEntityFactory(world));
	}

	@Override
	public void configure(WorldConfiguration worldConfig, Map map, CommandContext ctx, Zone zone) {

		worldConfig.register(api);
		
		worldConfig.setSystem(new ScriptCallableSystem());
	}

}
