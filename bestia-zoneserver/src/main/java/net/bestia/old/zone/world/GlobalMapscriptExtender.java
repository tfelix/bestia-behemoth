package net.bestia.zoneserver.zone.world;

import com.artemis.Entity;
import com.artemis.EntityEdit;
import com.artemis.World;
import com.artemis.WorldConfiguration;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.GlobalScript;
import net.bestia.zoneserver.ecs.system.GlobalMapScriptSystem;
import net.bestia.zoneserver.zone.Zone;
import net.bestia.zoneserver.zone.map.Map;

/**
 * Extends the map with the global mapscript.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class GlobalMapscriptExtender implements WorldExtend {
	

	@Override
	public void extend(World world, Map map, Zone zone) {
		final String globScript = map.getGlobalMapscript();
		
		final Entity e = world.createEntity();
		final EntityEdit ee = e.edit();
		
		final GlobalScript scriptComp = ee.create(GlobalScript.class);
		scriptComp.globalScriptName = globScript;
	}

	@Override
	public void configure(WorldConfiguration worldConfig, Map map, CommandContext ctx, Zone zone) {
		worldConfig.setSystem(new GlobalMapScriptSystem());
	}

}
