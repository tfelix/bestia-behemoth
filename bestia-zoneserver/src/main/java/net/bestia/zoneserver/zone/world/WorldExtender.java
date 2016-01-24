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
		if (config == null) {
			throw new IllegalArgumentException("Config can not be null.");
		}
		if (zone == null) {
			throw new IllegalArgumentException("Zone can not be null.");
		}

		final PackageLoader<WorldExtend> extendLoader = new PackageLoader<>(WorldExtend.class,
				"net.bestia.zoneserver.zone.world");
		final Set<WorldExtend> loadedExtras = extendLoader.getSubObjects();
		extras.addAll(loadedExtras);

		this.zone = zone;
	}

	/**
	 * Creates a world. Therefore it extends the map with all the extra entities
	 * described in the map..
	 * 
	 * @param CommandContext
	 *            Context to access general API of the zoneserver.
	 * @param map
	 *            The map is used to determine all the extras for the entity
	 *            world.
	 */
	public World createWorld(CommandContext ctx, Map map) {

		// Initialize ECS.
		final WorldConfiguration worldConfig = new WorldConfiguration();

		// Pre-configure the world.
		for (WorldExtend extra : extras) {
			extra.configure(worldConfig, map, ctx, zone);
		}
		
		final World world = new World(worldConfig);

		for (WorldExtend extra : extras) {
			extra.extend(world, map, zone);
		}

		return world;
	}
}
