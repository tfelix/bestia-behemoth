package net.bestia.zoneserver.ecs.manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;

import net.bestia.model.dao.BestiaDAO;
import net.bestia.model.domain.Bestia;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.MobGroup;
import net.bestia.zoneserver.ecs.component.MobSpawn;
import net.bestia.zoneserver.zone.spawn.Spawner;

/**
 * This manager keeps track about the dying NPC mob bestias. If the number is
 * below the number of the spawned bestias for this map a new spawning entity is
 * created.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class SpawnManager extends BaseEntitySystem {

	/**
	 * Helper class in oder to avoid re-creation of ints. We need a mutable
	 * Integer for the Hashmap.
	 *
	 */
	private class IntCounter {
		private int i = 0;

		public IntCounter(int i) {
			this.i = i;
		}

		public void inc() {
			i++;
		}

		public void dec() {
			i--;
		}

		public int get() {
			return i;
		}
	}

	private final static Logger LOG = LogManager.getLogger(SpawnManager.class);

	private ComponentMapper<MobGroup> mobGroupMapper;

	@Wire
	private CommandContext ctx;

	private final Map<String, Spawner> spawners = new HashMap<>();
	private final Map<String, IntCounter> counter = new HashMap<>();
	private final Map<String, Bestia> mobCache = new HashMap<>();

	public SpawnManager(List<Spawner> spawners) {
		super(Aspect.all(MobGroup.class));

		// Dont process this system. Only react passiv.
		setEnabled(false);

		for (Spawner sp : spawners) {
			this.spawners.put(sp.getMobName(), sp);
		}
	}

	@Override
	protected void initialize() {
		super.initialize();
	}

	@Override
	public void inserted(int eId) {
		final Entity entity = world.getEntity(eId);
		final String groupName = mobGroupMapper.get(entity).groupName;

		if (!counter.containsKey(groupName)) {
			counter.put(groupName, new IntCounter(1));
		} else {
			counter.get(groupName).inc();
		}
	}

	@Override
	public void removed(int eId) {
		final Entity entity = world.getEntity(eId);
		final String groupName = mobGroupMapper.get(entity).groupName;

		if (!counter.containsKey(groupName)) {
			return;
		}

		if (!spawners.containsKey(groupName)) {
			LOG.warn("No spawner for group name: {}.", groupName);
			return;
		}

		counter.get(groupName).dec();

		spawnMob(groupName, true);
	}

	/**
	 * Gets (and adds) a bestia to the local cache.
	 * 
	 * @param mobDbName
	 *            The mobDatabaseName of the bestia to add to the cache.
	 * @return The {@link Bestia} or NULL if the mobDbName was not found.
	 */
	private Bestia getCachedBestia(String mobDbName) {
		if (!mobCache.containsKey(mobDbName)) {
			// Load bestia into cache.
			final BestiaDAO dao = ctx.getServiceLocator().getBean(BestiaDAO.class);
			final Bestia bestia = dao.findByDatabaseName(mobDbName);

			if (bestia == null) {
				LOG.warn("Bestia with mobDbName: {} was not found in the database! Can not spawn.", mobDbName);

			}

			mobCache.put(mobDbName, bestia);
		}

		return mobCache.get(mobDbName);
	}

	/**
	 * Can be called in order to instantly spawn all entities managed by this
	 * manager. There will be no delay to the spawned entities.
	 */
	public void spawnAll() {
		// Iterate over all spawner.
		for (Map.Entry<String, Spawner> entry : spawners.entrySet()) {
			spawnMob(entry.getKey(), false);
		}
	}

	private void spawnMob(String groupName, boolean withDelay) {
		final Spawner spawner = spawners.get(groupName);
		if (spawner == null) {
			return;
		}

		// Get the difference between the should and is value. Create
		// spawn-entities.
		final IntCounter mobCount = counter.get(groupName);
		
		int diff = 0;
		
		if(mobCount == null) {
			diff = spawner.getMobCount();
		} else {
			diff = spawner.getMobCount() - counter.get(groupName).get();
		}

		while (diff-- > 0) {
			// Check if we have a bestia ready.
			final Bestia bestia = getCachedBestia(spawner.getMobName());
			if (bestia == null) {
				return;
			}

			// Create the spawn entities.
			final MobSpawn mobSpawn = world.createEntity().edit().create(MobSpawn.class);
			mobSpawn.coordinates = spawner.getNextSpawnLocation();
			// in ms not in seconds.
			if (withDelay) {
				mobSpawn.delay = spawner.getNextSpawnDelay() * 1000;
			} else {
				mobSpawn.delay = 100;
			}
			mobSpawn.mob = bestia;
		}
	}

	@Override
	protected void processSystem() {
		// is not called.
	}

}
