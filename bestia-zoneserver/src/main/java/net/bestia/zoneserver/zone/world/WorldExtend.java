package net.bestia.zoneserver.zone.world;

import net.bestia.zoneserver.zone.map.Map;

import com.artemis.World;

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
	 */
	public void extend(World world, Map map);
}
