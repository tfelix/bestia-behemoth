package net.bestia.zoneserver.generator.map;

import java.util.Date;
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

	/**
	 * Helper builder to create the map base parameter.
	 *
	 */
	public static class Builder {

		private long population;
		private Size worldSize;
		private float waterLandRatio;
		private int minSettlementDistance;
		private int settlementCount;
		private String name;

		public Builder() {
			// no op.
		}

		public void setPopulation(long population) {
			this.population = population;
		}

		public void setWorldSize(Size worldSize) {
			this.worldSize = worldSize;
		}

		public void setWaterLandRatio(float waterLandRatio) {
			this.waterLandRatio = waterLandRatio;
		}

		public void setMinSettlementDistance(int minSettlementDistance) {
			this.minSettlementDistance = minSettlementDistance;
		}

		public void setSettlementCount(int settlementCount) {
			this.settlementCount = settlementCount;
		}

		public void setName(String name) {
			this.name = name;
		}

		public MapBaseParameter build() {
			return new MapBaseParameter(this);
		}
	}

	private final static double MAP_RATIO = 12 / 8.0;
	private final static int MINIMUM_LANDMASS_SQUARE_KM = 40000;

	private final long population;
	private final Size worldSize;
	private final float waterLandRatio;
	private final int minSettlementDistance;
	private final int settlementCount;
	private final String name;
	private final Date createDate;

	public MapBaseParameter() {
		this.population = 100;
		this.worldSize = new Size(1000, 1000);
		this.waterLandRatio = 0.5f;
		this.minSettlementDistance = 500;
		this.settlementCount = 35;
		this.name = "";
		this.createDate = new Date();
	}

	public MapBaseParameter(Builder builder) {

		this.population = builder.population;
		this.worldSize = Objects.requireNonNull(builder.worldSize);
		this.waterLandRatio = builder.waterLandRatio;
		this.minSettlementDistance = builder.minSettlementDistance;
		this.settlementCount = builder.settlementCount;
		this.name = Objects.requireNonNull(builder.name);
		this.createDate = new Date();
	}

	/**
	 * Generates a new map for a average user count of players.
	 * 
	 * @param user
	 *            The number of players to generate the base parameter for.
	 * @return
	 */
	public static MapBaseParameter fromAverageUserCount(int user) {

		final Builder builder = new Builder();
		final ThreadLocalRandom rand = ThreadLocalRandom.current();

		double area = user * 0.5;
		float waterLandRatio = rand.nextInt(40, 60) / 100f;
		builder.setWaterLandRatio(waterLandRatio);

		if (area < MINIMUM_LANDMASS_SQUARE_KM) {
			area = MINIMUM_LANDMASS_SQUARE_KM;
		}

		area += area * (1 - waterLandRatio);

		final double baseSize = Math.sqrt(area);

		final double x = baseSize * MAP_RATIO;
		final double y = baseSize / MAP_RATIO;

		// Calculate the km to tile sizes (1 tile ~ 1m).
		final Size mapSize = new Size((int) (x * 1000), (int) (y * 1000));
		builder.setWorldSize(mapSize);

		int population = 6 * user;
		int numberOfSettlements = Math.max(30, 2 * population / 55) * (int) (rand.nextFloat() * 40);
		int minSettleDistance = rand.nextInt(4000, 6001);
		
		builder.setSettlementCount(numberOfSettlements);
		builder.setMinSettlementDistance(minSettleDistance);

		return builder.build();
	}
	
	public String getName() {
		return name;
	}
	
	public Date getCreateDate() {
		return createDate;
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

	@Override
	public String toString() {
		return String.format("MapBaseParams[size: %s, population: %d]", getWorldSize().toString(), population);
	}
}
