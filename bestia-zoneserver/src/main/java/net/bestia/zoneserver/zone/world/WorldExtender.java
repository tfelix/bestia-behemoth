package net.bestia.zoneserver.zone.world;

import java.util.HashSet;
import java.util.Set;

import com.artemis.World;
import com.artemis.WorldConfiguration;

import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.util.PackageLoader;
import net.bestia.zoneserver.zone.Zone;
import net.bestia.zoneserver.zone.map.Map;

/**
 * The world extender will take a world and a map and extend/spawn all the
 * needed scripts and extras which are described in the given map. The
 * WorldExtender will search all classes in this package who extend the
 * WorldExtra interface. Each of them has their specific feature.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class WorldExtender {

	private final Set<WorldExtend> extras = new HashSet<>();
	private final Zone zone;

	public WorldExtender(BestiaConfiguration config, Zone zone) {		
		if(config ==  null) {
			throw new IllegalArgumentException("Config can not be null.");
		}
		if(zone == null) {
			throw new IllegalArgumentException("Zone can not be null.");
		}
		
		final PackageLoader<WorldExtend> extendLoader = new PackageLoader<>(WorldExtend.class, "net.bestia.zoneserver.zone.world");
		final Set<WorldExtend> loadedExtras = extendLoader.getSubObjects();
		extras.addAll(loadedExtras);
		
		this.zone = zone;
	}

	public World createWorld(CommandContext ctx, Map map) {

		// Initialize ECS.
		final WorldConfiguration worldConfig = new WorldConfiguration();

		// Pre-configure the world.
		configure(worldConfig, map, ctx);

		final World world = new World(worldConfig);

		extend(world, map);

		return world;
	}

	/**
	 * Configures the world config before creating the world from it.
	 * 
	 * @param worldConfig
	 *            The artemis configuration.
	 * @param map
	 *            The map object.
	 * @param ctx 
	 */
	private void configure(WorldConfiguration worldConfig, Map map, CommandContext ctx) {
		for (WorldExtend extra : extras) {
			extra.configure(worldConfig, map, ctx, zone);
		}
	}

	/**
	 * Extends the given world with all the extra entities described in the
	 * given map.
	 * 
	 * @param world
	 *            The entity world to extend with the extras of the given map.
	 * @param map
	 *            The map is used to determine all the extras for the entity
	 *            world.
	 */
	private void extend(World world, Map map) {
		for (WorldExtend extra : extras) {
			extra.extend(world, map, zone);
		}
	}
}
