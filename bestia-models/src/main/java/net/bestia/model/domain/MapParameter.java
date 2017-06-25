package net.bestia.model.domain;

import java.io.Serializable;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import net.bestia.model.geometry.Size;

/**
 * Creates the base parameter of a newly generated map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "map_parameters")
public class MapParameter implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Helper builder to create the map base parameter.
	 *
	 */
	public static class Builder {

		private final Random rand = ThreadLocalRandom.current();

		private long population;
		private Size worldSize;
		private float waterLandRatio;
		private int minSettlementDistance;
		private int settlementCount;
		private String name;
		private int seed;

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

		public MapParameter build() {
			return new MapParameter(this);
		}

		public void newSeed() {
			this.seed = rand.nextInt();
		}
	}

	private final static double MAP_RATIO = 12 / 8.0;
	private final static int MINIMUM_LANDMASS_SQUARE_KM = 40000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;
	private long population;
	private Size worldSize;
	private float waterLandRatio;
	private int minSettlementDistance;
	private int settlementCount;
	private String name;
	private Date createDate;
	private int seed;

	public MapParameter() {
		this.population = 100;
		this.worldSize = new Size(1000, 1000);
		this.waterLandRatio = 0.5f;
		this.minSettlementDistance = 500;
		this.settlementCount = 35;
		this.name = "";
		this.createDate = new Date();
		this.seed = ThreadLocalRandom.current().nextInt();
	}

	public MapParameter(Builder builder) {

		this.population = builder.population;
		this.worldSize = Objects.requireNonNull(builder.worldSize);
		this.waterLandRatio = builder.waterLandRatio;
		this.minSettlementDistance = builder.minSettlementDistance;
		this.settlementCount = builder.settlementCount;
		this.name = Objects.requireNonNull(builder.name);
		this.createDate = new Date();
		this.seed = builder.seed;
	}

	/**
	 * Generates a new map for a average user count of players.
	 * 
	 * @param user
	 *            The number of players to generate the base parameter for.
	 * @return
	 */
	public static MapParameter fromAverageUserCount(int user, String mapName) {

		final Builder builder = new Builder();
		final ThreadLocalRandom rand = ThreadLocalRandom.current();

		builder.setName(mapName);

		double area = user * 0.5;
		float waterLandRatio = rand.nextInt(40, 60) / 100f;
		builder.setWaterLandRatio(waterLandRatio);

		if (area < MINIMUM_LANDMASS_SQUARE_KM) {
			area = MINIMUM_LANDMASS_SQUARE_KM;
		}

		area += area * (1 - waterLandRatio);

		// Calculate the km to tile sizes (1 tile ~ 1m).
		//final double baseSize = Math.sqrt(area);
		//final double x = baseSize * MAP_RATIO;
		//final double y = baseSize / MAP_RATIO;
		//final Size mapSize = new Size((int) (x * 1000), (int) (y * 1000));
		// FIXME We currently limit world size to 1sqm.
		builder.setWorldSize(new Size(1000, 1000));

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

	public int getSeed() {
		return seed;
	}

	@Override
	public String toString() {
		return String.format("MapParameter[size: %s, population: %d]", getWorldSize().toString(), population);
	}

	public String toDetailString() {
		return String.format(Locale.US,
				"MapParameter[width: %d height: %d, waterLandRatio: %.1f, population: %d, seed: %d, created: %s]",
				getWorldSize().getWidth(), getWorldSize().getHeight(), getWaterLandRatio(), getPopulation(), getSeed(),
				getCreateDate());
	}
}
