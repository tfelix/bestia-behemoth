package net.bestia.zoneserver.zone.world;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.zone.Zone;
import net.bestia.zoneserver.zone.map.Map;

import com.artemis.World;
import com.artemis.WorldConfiguration;

/**
 * Classes implementing this interface are automagically picked up by the
 * {@link WorldExtender} and are used to create all the needed entities to
 * translate between a bestia world-map and the ECS world.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
interface WorldExtend {

	/**
	 * This method is called and should create or modify the world accordingly
	 * to support the implemented feature.
	 * 
	 * @param world
	 *            The ECS world to extend.
	 * @param map
	 *            The Bestia map world.
	 * @param zone
	 *            The zone which the world instance belongs to.
	 */
	public void extend(World world, Map map, Zone zone);

	/**
	 * This is called before the world is created and the extend method is
	 * called. Can be used to alter the configuration of the world to be
	 * created.
	 * 
	 * @param worldConfig
	 *            Artemis {@link WorldConfiguration} can be changed and altered.
	 * @param map
	 *            The map of the bestia game.
	 * @param ctx
	 * @param zoneThe
	 *            zone which the worldConfig instance belongs to.
	 */
	public void configure(WorldConfiguration worldConfig, Map map, CommandContext ctx, Zone zone);
}
