package net.bestia.zoneserver.zone.map.generator;

import net.bestia.model.zone.Size;

/**
 * Calculates the size of the map depending on the average active user count.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class SizeCalculator {

	private final static double MAP_RATIO = 12 / 8.0;
	private final static double LANDMASS_WATER_RATIO = 0.5;
	private final static int MINIMUM_LANDMASS_SQUARE_KM = 40000;

	/**
	 * Calculates the map tile size of the worldmap depending on the number of
	 * users.
	 * 
	 * @param averageUserCount
	 * @return
	 */
	public Size getSize(int averageUserCount) {

		//
		double area = averageUserCount * 0.5;
		
		if(area < MINIMUM_LANDMASS_SQUARE_KM) {
			area = MINIMUM_LANDMASS_SQUARE_KM;
		}
		
		area += area * (1 - LANDMASS_WATER_RATIO);

		final double baseSize = Math.sqrt(area);
		
		final double x = baseSize * MAP_RATIO;
		final double y = baseSize / MAP_RATIO;

		// Calculate the km to tile sizes (1 tile ~ 1m).
		final Size mapSize = new Size((int)(x * 1000), (int)(y * 1000));

		return mapSize;
	}

}
