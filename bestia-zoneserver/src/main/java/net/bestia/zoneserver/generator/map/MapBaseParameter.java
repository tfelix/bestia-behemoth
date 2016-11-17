package net.bestia.zoneserver.generator.map;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import net.bestia.model.shape.Size;

/**
 * Creates the base parameter of a newly generated map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapBaseParameter {

	private final static double MAP_RATIO = 12 / 8.0;
	private final static int MINIMUM_LANDMASS_SQUARE_KM = 40000;

	private final long population;
	private final Size worldSize;
	private final float waterLandRatio;
	private final int minSettlementDistance;
	private final int settlementCount;

	public MapBaseParameter() {
		this.population = 100;
		this.worldSize = new Size(1000,1000);
		this.waterLandRatio = 0.5f;
		this.minSettlementDistance = 500;
		this.settlementCount = 35;
	}

	public MapBaseParameter(long population, Size size, float waterLandRatio, int numSettlements, int minSettleDistance) {
		
		this.population = population;
		this.worldSize = Objects.requireNonNull(size);
		this.waterLandRatio = waterLandRatio;
		this.minSettlementDistance = minSettleDistance;
		this.settlementCount = numSettlements;

	}

	/**
	 * Generates a new map for a average user count of players.
	 * 
	 * @param user
	 *            The number of players to generate the base parameter for.
	 * @return
	 */
	public static MapBaseParameter fromAverageUserCount(int user) {
		
		final ThreadLocalRandom rand = ThreadLocalRandom.current();
		
		double area = user * 0.5;
		float waterLandRatio = rand.nextInt(40, 60) / 100f;

		if (area < MINIMUM_LANDMASS_SQUARE_KM) {
			area = MINIMUM_LANDMASS_SQUARE_KM;
		}

		area += area * (1 - waterLandRatio);

		final double baseSize = Math.sqrt(area);

		final double x = baseSize * MAP_RATIO;
		final double y = baseSize / MAP_RATIO;

		// Calculate the km to tile sizes (1 tile ~ 1m).
		final Size mapSize = new Size((int) (x * 1000), (int) (y * 1000));

		int population = 6 * user;
		int numberOfSettlements = Math.max(30, 2 * population / 55) * (int)(rand.nextFloat() * 40);
		int minSettleDistance = rand.nextInt(4000, 6001);
		
		return new MapBaseParameter(population, mapSize, waterLandRatio, numberOfSettlements, minSettleDistance);
	}
	
	public long getPopulation() {
		return population;
	}

	public Size getWorldSize() {
		return worldSize;
	}

	public float getWaterLandRatio() {
		return waterLandRatio;
	}

	public int getMinSettlementDistance() {
		return minSettlementDistance;
	}

	public int getSettlementCount() {
		return settlementCount;
	}
}
