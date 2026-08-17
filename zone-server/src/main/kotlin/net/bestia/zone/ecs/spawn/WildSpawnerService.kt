package net.bestia.zone.ecs.spawn

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.mana.CorruptionStage
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.spawn.SpawnerChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.BestiaRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

/**
 * Turns the world's `BESTIA_SPAWN` markers into [Spawner] components, and decides which species each holds.
 *
 * ### Why there is no table here
 *
 * `MasterSpawnPointService` caches its equally-pure answer in `master_spawn_point`, and it is right to: an
 * account row references a chosen spawn point by identity, so that identity has to outlive the process.
 * A den is not stored because the markers are a pure function of the world seed and the world tier is
 * regenerated at boot. Storing them would be a table that can go stale against the generator that produced
 * it, in exchange for nothing.
 *
 * Something *does* reference a den, though, and that is why [Den] carries a [DenIdentity]: a persisted
 * creature records the den that made it, so it can be handed back to the same den after a restart instead of
 * being resurrected as an orphan. What is durable is the generator's own name for the marker - not a row
 * here, and emphatically not the den's ECS entity id, which is minted afresh on every boot.
 *
 * ### Species selection: one hard tier, one soft one
 *
 * A den says how hard it is and what kind of country it is in; the *catalogue* says what lives in country
 * like that. This used to be a single all-or-nothing join, and on the shipped world it left 644 of 656 dens
 * empty - because the only ordinary species is level 3 and almost no den's band contains it. A correct join
 * over a thin catalogue produces an empty world, which is worse than an approximate one.
 *
 * So the join is split by what a wrong answer actually costs:
 *
 * - **Hard, never relaxed.** [admits]: the species is not `eventOnly`, its boss-ness matches the den's, a
 *   `corruptedOnly` species is on corrupted ground, and - the one that matters - its `habitat` includes the
 *   den's biome. A level-three creature in a level-ninety den is a balance problem. A blob in a volcanic
 *   field is nonsense, and no amount of empty world justifies it.
 * - **Soft, relaxed when nothing exact fits.** The level band. [levelMissOf] measures how far outside it a
 *   species sits, the best available miss wins, and a miss of zero is an exact match. **The moment a species
 *   that fits exactly is authored, the best miss for that den becomes zero and every fallback is displaced -
 *   with no code change.** That is the property this shape exists for.
 * - **Soft, and not a tier at all.** Temperature, applied as a multiplier on `spawnWeight` in [weightOf]. A
 *   tier key would let a one-degree difference exclude four otherwise-perfect species from the draw; a
 *   weight makes them rarer instead. `wild-spawn.min-temperature-weight: 0` makes it effectively hard.
 *
 * A den that nothing *admits* is still dropped and counted. That count is now an honest measure of biome
 * coverage rather than of the level ramp, and with two species in the catalogue it is about half the world.
 *
 * Among what survives, one is drawn by weight. The draw is **seeded from the world seed and the den's
 * feature id**, not from a clock or a shared RNG, so the same den holds the same species on every boot of
 * the same world - which is what stops a server restart shuffling the whole bestiary.
 */
@Service
class WildSpawnerService(
  private val worldService: WorldService,
  private val bestiaRepository: BestiaRepository,
  private val config: WildSpawnConfig
) {

  /** One den ready to be placed into the ECS world. */
  data class Den(
    val identity: DenIdentity,
    val bestiaId: Long,
    val position: Vec3L,
    val pack: Int,
    val range: Int,
    val activationRange: Int
  )

  /**
   * Every den on the world, with a species chosen for each.
   *
   * Computed once and memoised for the life of the process - a pure function of the world, so it cannot go
   * stale, and `ChunkService.loaded by lazy` is the precedent for holding one in a field.
   */
  val dens: List<Den> by lazy { resolve() }

  private fun resolve(): List<Den> {
    val excluded = config.excludedSpecies.toSet()

    // `sortedBy { id }` is load bearing, not tidiness. The weighted draw walks the eligible list in order,
    // so the class's promise that a den holds the same species on every boot rested on MariaDB happening to
    // return rows in insertion order. Invisible with two species; a bestiary that reshuffles after a table
    // reorg with thirty.
    val all = bestiaRepository.findAll().sortedBy { it.id }
    val catalogue = all.filterNot { it.identifier in excluded }.map(::Candidate)

    val unmatched = excluded - all.map { it.identifier }.toSet()
    if (unmatched.isNotEmpty()) {
      LOG.warn { "wild-spawn.excluded-species names ${unmatched.joinToString()}, which no bestia matches" }
    }

    if (catalogue.isEmpty()) {
      LOG.warn { "No placeable bestia in the catalogue; every den on the world will stay empty" }
      return emptyList()
    }

    val record = worldService.record
    val stocking = stock(worldService.generated, record.seed, record.id, catalogue, config)

    LOG.info {
      "Wild dens: ${stocking.markers} markers, ${stocking.dens.size} stocked holding " +
          "${stocking.dens.sumOf { it.pack }} creature(s), ${stocking.thinned} thinned by wild-spawn.bands, " +
          "${stocking.unfilled} with no species admitted by their biome " +
          "(${all.size - catalogue.size} species excluded by config)"
    }

    if (stocking.clamped > 0) {
      LOG.warn {
        "${stocking.clamped} den(s) had their spawn radius clamped to ${config.maxRange} m, because " +
            "radius x radius-multiplier plus wild-spawn.activation-margin would exceed " +
            "SpawnerSystem.MAX_ACTIVATION_RANGE"
      }
    }

    if (stocking.fallbacks > 0) {
      LOG.warn {
        "${stocking.fallbacks} of ${stocking.markers} wild dens hold a species outside their own level band " +
            "(by band 1-8/9-40/41-79/80-100 = ${stocking.fallbacksByBand.joinToString("/")}, worst miss " +
            "${stocking.worstMiss} levels). This is the catalogue not covering the generator's ramp rather " +
            "than a fault in the join - a species authored inside a band displaces every fallback in it " +
            "automatically. Examples: ${stocking.samples.joinToString("; ")}"
      }
    }

    return stocking.dens
  }

  /**
   * What a den asks the catalogue for, read off its marker once.
   *
   * A named record rather than eight positional arguments because [pick] and [admits] both take it and the
   * two booleans are otherwise indistinguishable at a call site.
   */
  internal data class DenFacts(
    val levelMin: Int,
    val levelMax: Int,
    val biome: Biome,
    val corrupted: Boolean,
    val boss: Boolean,
    val temperature: Double
  )

  /**
   * A species with its habitat already parsed.
   *
   * Built once per boot rather than per den: `habitat.split(',')` inside the den loop is one allocation per
   * den per species, and there are tens of thousands of dens.
   */
  internal class Candidate(val species: Bestia) {
    val habitat: Set<Biome> =
      if (species.habitat.isEmpty()) {
        emptySet()
      } else {
        species.habitat.split(',')
          .mapNotNullTo(HashSet()) { name -> Biome.entries.firstOrNull { it.name == name } }
      }
  }

  /** Which species a den got, and how many levels outside its band that species sits. 0 is exact. */
  internal data class Choice(val species: Bestia, val levelMiss: Int)

  /** Everything one pass over the markers produced, including what it had to compromise on. */
  internal data class Stocking(
    val dens: List<Den>,
    val markers: Int,
    val thinned: Int,
    val unfilled: Int,
    val clamped: Int,
    val fallbacks: Int,
    val fallbacksByBand: IntArray,
    val worstMiss: Int,
    val samples: List<String>
  )

  internal companion object {
    private val LOG = KotlinLogging.logger { }

    /** How many misplaced dens to name in the warning. Enough to recognise the pattern, few enough to read. */
    private const val FALLBACK_SAMPLES = 5

    /**
     * Separate RNG salt for the band thinning.
     *
     * Without it the thinning roll and the species draw would come off the same `hash(seed, featureId)`, and
     * which dens survive would correlate with which species they would have held.
     */
    private const val DEN_SHARE_SALT = 0x5EEDL

    /**
     * The whole marker pass, as a pure function.
     *
     * `internal` and free of Spring, the repository and the database for the reason [pick] already was: this
     * is where every decision in this class lives, and it is worth being able to measure it against a
     * hand-built catalogue and a generated world with no context to stand up. `WildSpawnDensityTest` is what
     * that buys.
     */
    internal fun stock(
      generated: GeneratedWorld,
      worldSeed: Long,
      worldId: Long,
      catalogue: List<Candidate>,
      config: WildSpawnConfig
    ): Stocking {
      val worldConfig = generated.config

      val out = mutableListOf<Den>()
      val fallbacksByBand = IntArray(4)
      val samples = mutableListOf<String>()

      var markers = 0
      var thinned = 0
      var unfilled = 0
      var clamped = 0
      var fallbacks = 0
      var worstMiss = 0

      for (feature in generated.world.features.all()) {
        if (feature.kind != FeatureKind.BESTIA_SPAWN) continue
        val marker = feature as? PointMarker ?: continue
        markers++

        val levelMin = marker.attribute(SpawnerChannels.LEVEL_MIN).toInt()
        val levelMax = marker.attribute(SpawnerChannels.LEVEL_MAX).toInt()
        val boss = marker.attribute(SpawnerChannels.BOSS) >= 0.5

        val den = DenFacts(
          levelMin = levelMin,
          levelMax = levelMax,
          biome = Biome.entries[marker.attribute(SpawnerChannels.BIOME).toInt()],
          corrupted = marker.attribute(SpawnerChannels.CORRUPTION) >= CorruptionStage.CORRUPTED,
          boss = boss,
          temperature = marker.attribute(SpawnerChannels.TEMPERATURE)
        )

        val band = config.bandFor(levelMax)

        // Bosses are exempt: the generator already made them one per province, and dropping one drops
        // content rather than density.
        if (!boss && band.denShare < 1.0 &&
          GenRng.hashUnit(worldSeed, marker.id.value, DEN_SHARE_SALT) >= band.denShare
        ) {
          thinned++
          continue
        }

        val choice = pick(catalogue, worldSeed, marker.id.value, den, config)
        if (choice == null) {
          unfilled++
          continue
        }

        if (choice.levelMiss > 0) {
          fallbacks++
          worstMiss = maxOf(worstMiss, choice.levelMiss)
          fallbacksByBand[bandIndexOf(levelMax)]++
          if (samples.size < FALLBACK_SAMPLES) {
            samples.add(
              "den ${marker.id.value} at (${marker.position.x.toInt()},${marker.position.y.toInt()}) " +
                  "wanted $levelMin..$levelMax in ${den.biome.name} at ${den.temperature.roundToInt()}C, " +
                  "got ${choice.species.identifier} (level ${choice.species.level}, miss ${choice.levelMiss})"
            )
          }
        }

        val rawPack = marker.attribute(SpawnerChannels.PACK).toInt().coerceAtLeast(1)
        val pack = if (choice.species.boss) 1
        else (rawPack * band.packMultiplier).roundToInt().coerceIn(1, config.maxPack)

        // The clamp that stops `Spawner`'s init throwing at boot: it refuses a den whose activation range is
        // below its spawn range, and the cell index refuses one past MAX_ACTIVATION_RANGE. A radius
        // multiplier past about 1.4 would otherwise stop the server starting, which reads as a code bug.
        val wantedRange = (marker.attribute(SpawnerChannels.RADIUS) * band.radiusMultiplier)
          .roundToInt().coerceAtLeast(1)
        val range = wantedRange.coerceAtMost(config.maxRange)
        if (wantedRange > range) clamped++

        // Voxel indices, not metres. `ChunkCoords.VOXELS_PER_POSITION_UNIT` is 1 and `voxelSize` is 1 m, so
        // the two happen to coincide today - going through the conversion anyway is what keeps this correct
        // if either ever moves.
        val height = generated.base.heightAt(marker.position.x, marker.position.y)

        out.add(
          Den(
            identity = DenIdentity(
              featureId = marker.id.value,
              worldId = worldId,
              worldVersion = generated.world.pipelineVersion
            ),
            bestiaId = choice.species.id,
            position = Vec3L(
              x = (marker.position.x / worldConfig.voxelSize).toLong(),
              y = (marker.position.y / worldConfig.voxelSize).toLong(),
              z = ChunkCoords.standingZ(worldConfig, height)
            ),
            pack = pack,
            range = range,
            activationRange = (range + config.activationMargin)
              .coerceAtMost(SpawnerSystem.MAX_ACTIVATION_RANGE)
          )
        )
      }

      return Stocking(
        dens = out,
        markers = markers,
        thinned = thinned,
        unfilled = unfilled,
        clamped = clamped,
        fallbacks = fallbacks,
        fallbacksByBand = fallbacksByBand,
        worstMiss = worstMiss,
        samples = samples
      )
    }

    /** The `1-8 / 9-40 / 41-79 / 80-100` banding `Invariants.spawnerCensus` reports in. */
    internal fun bandIndexOf(levelMax: Int): Int = when {
      levelMax <= 8 -> 0
      levelMax <= 40 -> 1
      levelMax <= 79 -> 2
      else -> 3
    }

    /**
     * Draws a species for one den, deterministically.
     *
     * The stream is `hash(worldSeed, featureId)` rather than a shared generator, so which creature lives in
     * which den is a property of the world rather than of the order dens happened to be resolved in - and a
     * server restart does not shuffle the bestiary.
     */
    internal fun pick(
      catalogue: List<Candidate>,
      worldSeed: Long,
      featureId: Long,
      den: DenFacts,
      config: WildSpawnConfig
    ): Choice? {
      // The hard filter. Empty here means nothing the catalogue holds lives in country like this, which is
      // an honest null rather than a compromise to be made.
      val eligible = catalogue.filter { admits(it, den) }
      if (eligible.isEmpty()) return null

      // The soft one. `best == 0` is the exact tier; anything else is the fallback, and adding a species
      // that fits exactly moves `best` to 0 and drops every fallback out of the tier by itself.
      val best = eligible.minOf { levelMissOf(it, den) }
      val tier = eligible.filter { levelMissOf(it, den) == best }

      val weights = tier.map { weightOf(it, den, config) }
      val total = weights.sum()

      // `total == 0` only when min-temperature-weight is 0 and every candidate is out of window. A uniform
      // draw is the right answer there rather than an empty den: the tier already passed the hard filter, so
      // these species do belong here, they are merely all uncomfortable.
      val uniform = total <= 0.0
      var roll = GenRng.hashUnit(worldSeed, featureId) * (if (uniform) tier.size.toDouble() else total)

      for (i in tier.indices) {
        roll -= if (uniform) 1.0 else weights[i]
        if (roll < 0.0) return Choice(tier[i].species, best)
      }
      return Choice(tier.last().species, best)
    }

    /**
     * The constraints a den never bends, whatever it costs in empty world.
     *
     * Boss-ness both ways round: an ordinary den must not hold a boss either, or the one-per-province rarity
     * the generator arranged means nothing.
     */
    internal fun admits(candidate: Candidate, den: DenFacts): Boolean {
      val species = candidate.species

      if (species.eventOnly) return false
      if (den.boss != species.boss) return false
      if (species.corruptedOnly && !den.corrupted) return false
      if (candidate.habitat.isNotEmpty() && den.biome !in candidate.habitat) return false

      return true
    }

    /** How many levels outside the den's band this species sits. Zero is an exact fit. */
    internal fun levelMissOf(candidate: Candidate, den: DenFacts): Int = when {
      candidate.species.level < den.levelMin -> den.levelMin - candidate.species.level
      candidate.species.level > den.levelMax -> candidate.species.level - den.levelMax
      else -> 0
    }

    /**
     * `spawnWeight`, decayed by how far the den sits outside the species' temperature window.
     *
     * Linear to [WildSpawnConfig.temperatureFalloffCelsius] and floored at
     * [WildSpawnConfig.minTemperatureWeight], so a species is never made completely unspawnable by a window
     * somebody mistyped - which is the argument for the whole axis being soft.
     */
    internal fun weightOf(candidate: Candidate, den: DenFacts, config: WildSpawnConfig): Double {
      val base = candidate.species.spawnWeight.coerceAtLeast(1).toDouble()
      val low = candidate.species.temperatureMinCelsius ?: return base
      val high = candidate.species.temperatureMaxCelsius ?: return base

      val overshoot = maxOf(low - den.temperature, den.temperature - high, 0.0)
      if (overshoot <= 0.0) return base

      val decayed = 1.0 - overshoot / config.temperatureFalloffCelsius
      return base * decayed.coerceAtLeast(config.minTemperatureWeight)
    }
  }
}
