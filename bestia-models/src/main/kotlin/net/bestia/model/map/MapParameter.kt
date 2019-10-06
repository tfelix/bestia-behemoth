package net.bestia.model.map

import net.bestia.model.AbstractEntity
import net.bestia.model.geometry.Size
import java.io.Serializable
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom
import javax.persistence.Entity
import javax.persistence.Table

/**
 * Creates the base parameter of a newly generated map.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "map_parameters")
class MapParameter(
    /**
     * Projected number of NPC to this world.
     *
     * @return Number of NPCs in this world.
     */
    val population: Long = 0,

    val width: Long = 1000,

    val depth: Long = 1000,

    /**
     * Projected ratio between water and land.
     *
     * @return
     */
    val waterLandRatio: Float = 0f,
    /**
     * Minimum distance between settlements.
     *
     * @return Minimum distance between settlements in meters.
     */
    val minSettlementDistance: Int = 0,
    /**
     * The number of settlements in this world.
     *
     * @return Minimum distance between settlements in meters/tiles.
     */
    val settlementCount: Int = 0,
    /**
     * The name of this world.
     *
     * @return The name of this world.
     */
    val name: String,
    /**
     * @return The date of the worlds creation.
     */
    val createDate: Instant = Instant.now(),
    /**
     * The seed number for the random number generator. This is important to
     * recreate this world. Even though it might be possible that not all
     * details can be restored via this one single seed most of it should.
     *
     * @return The world seed.
     */
    val seed: Int = 0
) : AbstractEntity(), Serializable {

  override fun toString(): String {
    return "MapParameter[name: $name, size: $worldSize, population: $population]"
  }

  fun toDetailString(): String {
    return "MapParameter[size: $worldSize, waterLandRatio: $waterLandRatio, population: $population, seed: $seed, created: $createDate]"
  }

  val worldSize: Size
    get() = Size(width, 3000, depth)

  companion object {
    private const val MINIMUM_LANDMASS_SQUARE_KM = 40000

    fun newDefault(name: String): MapParameter {
      return MapParameter(
          population = 100,
          width = 1000,
          depth = 1000,
          waterLandRatio = 0.5f,
          minSettlementDistance = 500,
          settlementCount = 5,
          name = name,
          seed = ThreadLocalRandom.current().nextInt()
      )
    }

    /**
     * Generates a new map for a average user count of players.
     *
     * @param user
     * The number of players to generate the base parameter for.
     * @return
     */
    fun fromAverageUserCount(user: Int, mapName: String): MapParameter {
      val rand = ThreadLocalRandom.current()

      var area = user * 0.5
      val waterLandRatio = rand.nextInt(40, 60) / 100f
      if (area < MINIMUM_LANDMASS_SQUARE_KM) {
        area = MINIMUM_LANDMASS_SQUARE_KM.toDouble()
      }
      area += area * (1 - waterLandRatio)

      // Calculate the km to tile sizes (1 tile ~ 1m).
      // final double baseSize = Math.sqrt(area);
      // final double x = baseSize * MAP_RATIO;
      // final double y = baseSize / MAP_RATIO;
      // final Size mapSize = new Size((int) (x * 1000), (int) (y * 1000));
      // FIXME We currently limit world size to 1sqm.
      val worldSize = Size(1000, 3000, depth = 1000)

      val population = 6L * user
      val numberOfSettlements = Math.max(30, 2 * population / 55) * (rand.nextFloat() * 40).toInt()
      val minSettleDistance = rand.nextInt(4000, 6001)

      return MapParameter(
          population = population,
          width = worldSize.width,
          depth = worldSize.depth,
          seed = rand.nextInt(),
          name = mapName,
          settlementCount = numberOfSettlements.toInt(),
          minSettlementDistance = minSettleDistance,
          waterLandRatio = waterLandRatio
      )
    }
  }
}
