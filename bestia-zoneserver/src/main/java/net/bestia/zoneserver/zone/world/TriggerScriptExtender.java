package net.bestia.zoneserver.zone.world;

import com.artemis.World;
import com.artemis.WorldConfiguration;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.system.TriggerScriptSystem;
import net.bestia.zoneserver.zone.Zone;
import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.map.MapTriggerScript;
import net.bestia.zoneserver.zone.shape.CollisionShape;

/**
 * Inserts all the scripts into the map which are triggered if someone steps
 * onto them.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TriggerScriptExtender implements WorldExtend {

	private TriggerScriptSystem system = new TriggerScriptSystem();

	@Override
	public void extend(World world, Map map, Zone zone) {

		for (MapTriggerScript script : map.getScripts()) {

			final String name = script.getScriptName();
			final CollisionShape shape = script.getShape();

			system.createTriggerScript(name, shape);
		}

	}

	@Override
	public void configure(WorldConfiguration worldConfig, Map map, CommandContext ctx, Zone zone) {

		worldConfig.setSystem(system);

	}

}
