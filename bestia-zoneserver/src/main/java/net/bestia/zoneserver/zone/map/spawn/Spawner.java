package net.bestia.zoneserver.zone.map.spawn;

import java.util.Random;

import net.bestia.model.shape.Point;

/**
 * The {@link Spawner} manages data for the mob to spawn, the spawn delay and
 * the locations where the mob should be spawned.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Spawner {

	private final String mobDbName;
	private final int minDelay;
	private final int deltaDelay;
	private final SpawnLocation location;
	private final int mobCount;
	private final Random rand = new Random();

	public Spawner(String mobDbName, SpawnLocation location, int minDelay, int maxDelay, int count) {
		if (minDelay > maxDelay) {
			throw new IllegalArgumentException("minDelay must be smaller than maxDelay");
		}

		if (minDelay < 0 || maxDelay < 0) {
			throw new IllegalArgumentException("minDelay and maxDelay must be positive.");
		}

		if (mobDbName == null || mobDbName.isEmpty()) {
			throw new IllegalArgumentException("MobDbName can not be null or empty.");
		}

		if (location == null) {
			throw new IllegalArgumentException("Location can not be null.");
		}

		this.mobDbName = mobDbName;
		this.minDelay = minDelay;
		this.deltaDelay = maxDelay - minDelay;
		this.location = location;
		this.mobCount = count;
	}

	/**
	 * Returns the mob database name.
	 * 
	 * @return The mob database name.
	 */
	public String getMobName() {
		return mobDbName;
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
	public Point getNextSpawnLocation() {
		return location.getSpawn();
	}

	/**
	 * The designated number of this mob on this map.
	 * 
	 * @return The designated mob entity number.
	 */
	public int getMobCount() {
		return mobCount;
	}
}
