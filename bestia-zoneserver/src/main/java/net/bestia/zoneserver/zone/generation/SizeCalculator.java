package net.bestia.zoneserver.zone.generation;

/**
 * Calculates the size of the map depending on the average active user count.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class SizeCalculator {

	public Size getSize(int averageUserCount) {

		final int km2 = averageUserCount * 50;

		final int size = (int) Math.sqrt(km2);
		
		final Size mapSize = new Size(size * 1000, size * 1000);
		
		return mapSize;
	}

}
