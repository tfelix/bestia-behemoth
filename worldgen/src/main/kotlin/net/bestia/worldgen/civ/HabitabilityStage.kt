package net.bestia.worldgen.civ

import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.resource.ResourceStage

/** Tuning for [HabitabilityStage]. */
data class HabitabilityParams(

  /** The culture whose weights the emitted [LayerId.HABITABILITY] layer uses. */
  val culture: Culture = Culture.AGRARIAN,

  /** Distance over which access to fresh water decays, in metres. */
  val waterRange: Double = 6_000.0,

  /** Discharge in cubic metres per second at which a watercourse counts as a fresh water source. */
  val waterDischarge: Double = 0.6,

  /** Distance over which harbour access decays from the shoreline, in metres. */
  val harbourRange: Double = 4_000.0,

  /** Radius over which local prominence is measured for defensibility, in cells. */
  val prominenceRadius: Int = 4,

  /** Ideal mean annual temperature in degrees. */
  val comfortTemperature: Double = 13.0,

  /** Degrees either side of the ideal before comfort has fallen to nothing. */
  val comfortTolerance: Double = 24.0,

  /** Slope beyond which ground is not worth ploughing. */
  val arableSlope: Double = 0.06,

  /** Multiplier for the movement cost of crossing a river. */
  val riverCrossingCost: Double = 8.0
)

/**
 * Stage 7: the habitability field, and the movement cost field roads will be routed over.
 *
 * Habitability is a weighted sum of terms that each answer a question a founder would actually ask: is there
 * drinking water, will crops grow, is the ground ploughable, can it be defended, is there anything worth
 * digging up, is the weather survivable, can a ship shelter here, and is it going to flood.
 *
 * Several of those are much better answered against the *vector* tier than against a raster, and are:
 * distance to fresh water comes from the river polylines rather than from a discharge threshold on a
 * kilometre grid, so a village can sit two hundred metres from a stream rather than somewhere in the same
 * square kilometre as one. Harbour quality comes from coastline concavity, which is what distinguishes a
 * sheltered inlet from an exposed headland - and it is why fjord country scores so well.
 *
 * The stage emits one habitability layer, for [HabitabilityParams.culture]. Other cultures score the same
 * terms with their own weights at placement time; see [SettlementStage]. Emitting one layer per culture would
 * be four times the memory for a field that is only read once.
 */
class HabitabilityStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: HabitabilityParams = HabitabilityParams()
) : Stage {

  override val id = ID
  override val version = 1
  override val dependencies = listOf(
    ClimateStage.ID, ErosionStage.ID, HydrologyStage.ID, BiomeStage.ID, ResourceStage.ID
  )
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.HABITABILITY),
    StageOutput.Raster(LayerId.MOVEMENT_COST)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val terms = Terms.read(ctx, region, params)

    val habitability = Grid(region.width, region.height)
    for (i in habitability.data.indices) {
      habitability.data[i] = terms.scoreAt(i, params.culture)
    }

    return StageResult.of(
      habitability.toLayer(LayerId.HABITABILITY, region),
      terms.movementCost.toLayer(LayerId.MOVEMENT_COST, region)
    )
  }

  companion object {
    val ID = StageId("habitability")
  }
}


