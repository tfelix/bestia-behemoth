package net.bestia.worldgen.civ

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.WorldWrap
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.cos
import kotlin.math.sin

/**
 * Picks a small set of "home settlements" a new master can choose to start life near: the
 * settlements ranked 2nd, 3rd and 4th largest by population - never the largest, which is the capital
 * everyone already knows about - each with a safe coordinate on solid ground clear of its own
 * built-up area.
 *
 * A pure function over [GeneratedWorld], like the rest of `worldgen`. `zone-server` decides whether
 * and how to cache the result; this only ever recomputes it from scratch.
 */
object SettlementSpawnPoints {

  data class Candidate(
    val settlementIndex: Int,
    val name: String,
    val tier: SettlementTier,
    val population: Int,
    val position: Vec2d
  )

  /** How many standing settlements exist. Used to enforce the "at least 2" boot guard. */
  fun standingSettlementCount(generated: GeneratedWorld): Int =
    standingSettlementsByPopulationDesc(generated).size

  /**
   * Up to [maxCandidates] settlements ranked 2nd..(maxCandidates+1) largest by population, each with
   * a safe home coordinate. Fewer than [maxCandidates] if the world does not have that many; a
   * settlement that cannot find safe land nearby (an extreme edge case - a settlement almost
   * entirely surrounded by sea beyond its own footprint) is silently dropped, so the result can also
   * be shorter for that reason.
   */
  fun choose(generated: GeneratedWorld, maxCandidates: Int = MAX_HOME_CANDIDATES): List<Candidate> {
    val ranked = standingSettlementsByPopulationDesc(generated)
    if (ranked.size <= 1) return emptyList()

    val sites = settlementSitesByIndex(generated)
    val seed = generated.config.seed

    return ranked.drop(1).take(maxCandidates).mapNotNull { index ->
      val site = sites[index] ?: return@mapNotNull null
      val record = generated.world.chronicle.settlements[index]
      val cultureIndex = site.attribute(SettlementChannels.CULTURE).toInt()
      val tier = SettlementTier.entries[site.attribute(SettlementChannels.TIER).toInt()]

      // A plain MIN_DISTANCE_METRES can land inside a city/town's own built footprint, so the real
      // placement distance never comes in closer than the settlement's footprint plus clearance.
      val distance = maxOf(MIN_DISTANCE_METRES, tier.footprintRadius + FOOTPRINT_CLEARANCE_METRES)

      val position = findLandNear(generated, site.position, seed, index, distance) ?: return@mapNotNull null

      Candidate(
        settlementIndex = index,
        name = Names.place(record.nameSeed, cultureIndex),
        tier = tier,
        population = record.population,
        position = position
      )
    }
  }

  /**
   * Which settlement indices are home candidates, from a map of index to population.
   *
   * The rule itself - **second largest down, never the capital** - extracted so it has one definition. The
   * spawn stage needs the same answer to decide where the level-one safety ring goes, and it cannot call
   * [choose]: that needs an assembled `GeneratedWorld` for the ground test, which does not exist while the
   * pipeline is still running. Two copies of "who the home villages are" would agree until one of them moved.
   */
  fun homeCandidateIndices(populations: Map<Int, Int>): List<Int> =
    populations.entries
      .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
      .drop(1)
      .take(MAX_HOME_CANDIDATES)
      .map { it.key }

  private fun standingSettlementsByPopulationDesc(generated: GeneratedWorld): List<Int> {
    val chronicle = generated.world.chronicle
    return chronicle.settlements.indices
      .filter { chronicle.settlementStood(it, chronicle.presentYear) }
      .sortedWith(compareByDescending { chronicle.settlements[it].population })
  }

  private fun settlementSitesByIndex(generated: GeneratedWorld): Map<Int, PointMarker> =
    generated.world.features.all()
      .asSequence()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .associateBy { it.attribute(SettlementChannels.INDEX).toInt() }

  /**
   * Walks outward from [center] at [startDistance], trying [BEARING_ATTEMPTS] deterministic bearings
   * before widening the ring by [DISTANCE_STEP_METRES] and trying again, up to [MAX_DISTANCE_ATTEMPTS]
   * rings. The stream is derived from the world seed and the settlement's own index, so the chosen
   * spot is stable across regenerations of the same seed without needing to store the bearing.
   */
  private fun findLandNear(
    generated: GeneratedWorld,
    center: Vec2d,
    seed: Long,
    settlementIndex: Int,
    startDistance: Double
  ): Vec2d? {
    val rng = GenRng(GenRng.hash(seed, settlementIndex.toLong()))
    val wrap = WorldWrap(generated.config)

    var distance = startDistance
    repeat(MAX_DISTANCE_ATTEMPTS) {
      repeat(BEARING_ATTEMPTS) {
        val angle = rng.nextDouble(0.0, 2.0 * Math.PI)
        val rawX = center.x + cos(angle) * distance
        val rawY = center.y + sin(angle) * distance
        val normalised = wrap.normalise(rawX, rawY)

        if (generated.base.heightAt(normalised.x, normalised.y) > generated.config.seaLevel) {
          return Vec2d(normalised.x, normalised.y)
        }
      }
      distance += DISTANCE_STEP_METRES
    }

    return null
  }

  /**
   * How many settlements a new master may choose between: the 2nd, 3rd and 4th largest standing ones.
   *
   * Hoisted from [choose]'s default so [homeCandidateIndices] and the spawn stage's safety ring cannot
   * disagree about how many towns count as home.
   */
  const val MAX_HOME_CANDIDATES = 3

  /**
   * Furthest a chosen home coordinate can sit from its settlement's own centre, in metres.
   *
   * [findLandNear] starts at the footprint plus clearance and widens by [DISTANCE_STEP_METRES] up to
   * [MAX_DISTANCE_ATTEMPTS] times, so this is the worst case for the largest tier. It exists because the
   * **spawn stage cannot see the arrival point** - it runs inside the pipeline, and choosing one needs an
   * assembled world - so its safety ring has to be drawn around the centre wide enough to contain any
   * arrival point plus the ring the player is actually owed.
   *
   * Without it the two disagree in the direction that matters: a den 2.9 km from where a master appears can
   * be 4.4 km from the town centre, which a 3 km ring around the centre does not cover.
   */
  val MAX_ARRIVAL_OFFSET_METRES: Double
    get() = SettlementTier.entries.maxOf { it.footprintRadius } + FOOTPRINT_CLEARANCE_METRES +
        DISTANCE_STEP_METRES * MAX_DISTANCE_ATTEMPTS

  /** Minimum clearance from a spawn point to the settlement it is near, in metres. */
  private const val MIN_DISTANCE_METRES = 300.0

  /** Extra clearance kept beyond a settlement's own footprint radius, in metres. */
  private const val FOOTPRINT_CLEARANCE_METRES = 50.0

  /** Bearings tried per distance ring before the ring is widened. */
  private const val BEARING_ATTEMPTS = 24

  /** Distance a ring is widened by, this many times, before a candidate is given up on. */
  private const val DISTANCE_STEP_METRES = 150.0
  private const val MAX_DISTANCE_ATTEMPTS = 8
}
