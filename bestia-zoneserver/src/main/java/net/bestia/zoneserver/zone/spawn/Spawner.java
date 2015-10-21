package net.bestia.zoneserver.zone.spawn;

import java.util.Random;

import net.bestia.model.domain.Bestia;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * The {@link Spawner} manages data for the mob to spawn, the spawn delay and
 * the locations where the mob should be spawned.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Spawner {

	private final Bestia mob;
	private final int minDelay;
	private final int deltaDelay;
	private final SpawnLocation location;
	private final Random rand = new Random();

	public Spawner(Bestia mob, SpawnLocation location, int minDelay, int maxDelay) {
		if (minDelay > maxDelay) {
			throw new IllegalArgumentException("minDelay must be smaller than maxDelay");
		}

		if (minDelay < 0 || maxDelay < 0) {
			throw new IllegalArgumentException("minDelay and maxDelay must be positive.");
		}

		if (mob == null) {
			throw new IllegalArgumentException("Mob can not be null.");
		}

		if (location == null) {
			throw new IllegalArgumentException("Location can not be null.");
		}

		this.mob = mob;
		this.minDelay = minDelay;
		this.deltaDelay = maxDelay - minDelay;
		this.location = location;
	}

	/**
	 * Returns the mob database name.
	 * 
	 * @return The mob database name.
	 */
	public String getMobName() {
		return mob.getDatabaseName();
	}

	/**
	 * The id of the mob bestia.
	 * 
	 * @return The id of the mob.
	 */
	public int getMobId() {
		return mob.getId();
	}

	/**
	 * Return the next spawn of the this mob in seconds.
	 * 
	 * @return Delay until next spawn in s.
	 */
	public int getNextSpawnDelay() {
		return minDelay + rand.nextInt(deltaDelay);
	}

	/**
	 * Returns the next spawn location of this mob.
	 * 
	 * @return The next spawn location for this mob.
	 */
	public Vector2 getNextSpawnLocation() {
		return location.getSpawn();
	}
}
