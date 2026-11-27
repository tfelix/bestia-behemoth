package net.bestia.zone.doomsday.withering

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.doomsday.DoomFate
import net.bestia.zone.doomsday.DoomKind
import net.bestia.zone.doomsday.DoomShape
import net.bestia.zone.doomsday.DoomStage
import net.bestia.zone.doomsday.DoomThreat
import net.bestia.zone.doomsday.persistence.StoredCorruptionCrystal
import net.bestia.zone.doomsday.persistence.StoredCorruptionCrystalRepository
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.worldevent.EventGround
import org.springframework.stereotype.Component
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Withering: a front of corruption that spreads from the land most blighted in the world's history. It grows only
 * from its corruption crystals: each one corrupts the land around it, and new ones sprout at the edge of the old ones
 * until the front covers what its clock asks. Breaking a crystal stops its growth, and the land under it heals. The
 * plants on rotten land die for good ([WitheringBlight]).
 *
 * The players hold it where they keep the crystals below what the clock asks for.
 */
@Component
class WitheringThreat(
  private val config: WitheringConfig,
  private val field: CorruptionField,
  private val journal: CorruptionJournal,
  private val rows: StoredCorruptionCrystalRepository,
  private val crystals: CorruptionCrystals,
  private val blight: WitheringBlight,
  private val provinces: ProvinceService,
  private val ground: EventGround,
  private val worldService: WorldService,
  private val random: Random = Random.Default,
) : DoomThreat {

  override val kind = DoomKind.WITHERING

  override val shape = DoomShape.FRONT

  /** The most corrupted land cell outside the sanctuaries, in metres. */
  private val mostBlighted: Vec2d? by lazy { findMostBlighted() }

  @Volatile
  private var containment = 0.0

  override fun fits(): Boolean {
    return mostBlighted != null
  }

  override fun origin(random: Random): Vec2d {
    return checkNotNull(mostBlighted) { "The Withering does not fit this world" }
  }

  /** How far the players keep the crystals below what the clock asks for. */
  override fun containment(): Double {
    return containment
  }

  override fun step(fate: DoomFate, realSeconds: Double): Double {
    rows.deleteAllById(crystals.takeBroken())
    if (fate.stage < DoomStage.ONSET) {
      return 0.0
    }

    val standing = rows.findAll().toMutableList()
    val target = targetCrystals(fate.progress)
    sprout(fate, standing, target, realSeconds)
    containment = (1.0 - standing.size.toDouble() / target).coerceIn(0.0, 1.0)

    spread(standing, realSeconds / SECONDS_PER_DAY)
    blight.killPlants()
    crystals.keep(standing, levelAt(fate.progress))
    journal.flush()
    return 0.0
  }

  /** New crystals at the edge of the old ones, as many as the clock asks for and a day's growth allows. */
  private fun sprout(fate: DoomFate, standing: MutableList<StoredCorruptionCrystal>, target: Int, realSeconds: Double) {
    val expected = config.crystalsPerDay * realSeconds / SECONDS_PER_DAY
    val allowed = floor(expected).toInt() + if (random.nextDouble() < expected - floor(expected)) 1 else 0
    repeat(minOf(allowed, target - standing.size).coerceAtLeast(0)) {
      val parent = standing.randomOrNull(random)?.let { Vec2d(it.x, it.y) } ?: Vec2d(fate.originX, fate.originY)
      spotNear(parent, standing)?.let { standing += rows.save(StoredCorruptionCrystal(it.x, it.y)) }
    }
  }

  private fun spotNear(parent: Vec2d, standing: List<StoredCorruptionCrystal>): Vec2d? {
    repeat(SPOT_TRIES) {
      val angle = random.nextDouble(0.0, 2 * Math.PI)
      val distance = if (standing.isEmpty()) 0.0 else random.nextDouble(config.crystalSpacingMetres, config.crystalReachMetres)
      val x = parent.x + cos(angle) * distance
      val y = parent.y + sin(angle) * distance
      val crowded = standing.any { hypot(it.x - x, it.y - y) < config.crystalSpacingMetres }
      if (!crowded && !provinces.isSanctuaryAt(x, y) && ground.grounded(x, y) != null) {
        return Vec2d(x, y)
      }
    }
    return null
  }

  /** Corruption rises under every crystal, strongest at its foot, and heals where none stands any more. */
  private fun spread(standing: List<StoredCorruptionCrystal>, days: Double) {
    synchronized(field) {
      val reached = HashMap<ManaField.Cell, Double>()
      for (crystal in standing) {
        cellsWithin(crystal.x, crystal.y, config.crystalReachMetres).forEach { cell ->
          val nearness = 1.0 - hypot(cell.centreX - crystal.x, cell.centreY - crystal.y) / config.crystalReachMetres
          reached.merge(cell, nearness, ::maxOf)
        }
      }

      reached.forEach { (cell, nearness) -> field.change(cell, config.corruptPerDay * days * nearness) }
      field.corrupted()
        .filter { (cell, _) -> cell !in reached }
        .forEach { (cell, _) -> field.change(cell, -config.healPerDay * days) }
      field.dropHealed()
    }
  }

  private fun cellsWithin(x: Double, y: Double, radius: Double): List<ManaField.Cell> {
    val reach = (radius / ManaField.CELL_METRES).toInt() + 1
    val centreX = ManaField.cellOf(x)
    val centreY = ManaField.cellOf(y)
    return (centreY - reach..centreY + reach).flatMap { cy ->
      (centreX - reach..centreX + reach).map { cx -> ManaField.Cell(cx, cy) }
    }.filter { hypot(it.centreX - x, it.centreY - y) <= radius }
  }

  private fun targetCrystals(progress: Double): Int {
    val unfolded = ((progress - DoomStage.ONSET.from) / (1.0 - DoomStage.ONSET.from)).coerceIn(0.0, 1.0)
    return maxOf(1, (config.maxCrystals * unfolded.pow(GROWTH_CURVE)).roundToInt())
  }

  private fun levelAt(progress: Double): Int {
    return (config.crystalLevelAtOnset + (config.crystalLevelAtEnd - config.crystalLevelAtOnset) * progress).roundToInt()
  }

  private fun findMostBlighted(): Vec2d? {
    val world = worldService.generated.world
    val corruption = world.layers[LayerId.CORRUPTION] as? FloatLayer ?: return null
    val elevation = world.layers[LayerId.ELEVATION] as? FloatLayer ?: return null
    val water = world.layers[LayerId.WATER_LEVEL] as? FloatLayer ?: return null
    val seaLevel = worldService.generated.config.seaLevel
    val region = corruption.region
    val cellMetres = region.resolution.metresPerCell

    var best: Vec2d? = null
    var bestCorruption = 0f
    for (i in corruption.data.indices) {
      val dry = elevation.data[i] > seaLevel && water.data[i].isNaN()
      if (!dry || corruption.data[i] <= bestCorruption) {
        continue
      }
      val x = (region.minX + i % region.width + 0.5) * cellMetres
      val y = (region.minY + i / region.width + 0.5) * cellMetres
      if (!provinces.isSanctuaryAt(x, y)) {
        best = Vec2d(x, y)
        bestCorruption = corruption.data[i]
      }
    }
    return best
  }

  private companion object {
    const val SECONDS_PER_DAY = 86_400.0
    const val SPOT_TRIES = 8

    /** Slow at first, then faster: the front is small for long, and grows wide near the end. */
    const val GROWTH_CURVE = 1.3
  }
}
