package net.bestia.zoneserver.zone.world;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.PlayerManager;
import com.artemis.managers.TagManager;
import com.artemis.managers.UuidEntityManager;

import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.manager.WorldPersistenceManager;
import net.bestia.zoneserver.ecs.system.AISystem;
import net.bestia.zoneserver.ecs.system.ActiveSpawnUpdateSystem;
import net.bestia.zoneserver.ecs.system.ChangedNetworkUpdateSystem;
import net.bestia.zoneserver.ecs.system.ChatSystem;
import net.bestia.zoneserver.ecs.system.DelayedRemoveSystem;
import net.bestia.zoneserver.ecs.system.InputSystem;
import net.bestia.zoneserver.ecs.system.MapScriptSystem;
import net.bestia.zoneserver.ecs.system.MobSpawnSystem;
import net.bestia.zoneserver.ecs.system.MovementSystem;
import net.bestia.zoneserver.ecs.system.PersistSystem;
import net.bestia.zoneserver.ecs.system.VisibleSpawnUpdateSystem;
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

	private static final Logger log = LogManager.getLogger(WorldExtender.class);

	private final BestiaConfiguration config;
	private final Set<WorldExtend> extras = new HashSet<>();

	public WorldExtender(BestiaConfiguration config) {
		this.config = config;

		final Reflections reflections = new Reflections("net.bestia.zoneserver.zone.world");
		final Set<Class<? extends WorldExtend>> subTypes = reflections.getSubTypesOf(WorldExtend.class);

		for (Class<? extends WorldExtend> clazz : subTypes) {

			// Dont instance abstract classes.
			if (Modifier.isAbstract(clazz.getModifiers())) {
				continue;
			}

			try {
				final WorldExtend extra = clazz.newInstance();
				extras.add(extra);
			} catch (InstantiationException | IllegalAccessException e) {
				log.error("Can not instanciate command handler: {}", clazz.toString(), e);
			}
		}
	}

	public World createWorld(CommandContext ctx, Map map) {

		final File saveFolder = new File(config.getProperty("zone.persistFolder"));

		// Initialize ECS.
		final WorldConfiguration worldConfig = new WorldConfiguration();
		// Register all external helper objects.
		worldConfig.register(map);
		worldConfig.register(ctx);
		worldConfig.register(ctx.getServer().getBestiaRegister());

		// Set all the systems.
		worldConfig.setSystem(new MobSpawnSystem());
		worldConfig.setSystem(new InputSystem());
		worldConfig.setSystem(new MovementSystem());
		worldConfig.setSystem(new AISystem());
		worldConfig.setSystem(new ChatSystem());
		worldConfig.setSystem(new ActiveSpawnUpdateSystem());
		worldConfig.setSystem(new VisibleSpawnUpdateSystem());
		worldConfig.setSystem(new MapScriptSystem());
		worldConfig.setSystem(new DelayedRemoveSystem());
		worldConfig.setSystem(new PersistSystem(10000));
		// ChangedNetworkUpdateSystem must be last because it removes the
		// Changed component.
		worldConfig.setSystem(new ChangedNetworkUpdateSystem());

		// Set all the managers.
		worldConfig.setSystem(new PlayerManager());
		worldConfig.setSystem(new TagManager());
		worldConfig.setSystem(new UuidEntityManager());
		worldConfig.setSystem(new WorldPersistenceManager(saveFolder, map.getMapDbName()));

		final World world = new World(worldConfig);

		extend(world, map);

		return world;
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
			extra.extend(world, map);
		}
	}
}
