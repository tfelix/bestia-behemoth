package net.bestia.zone.ecs.spawn.ambient

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.mana.CorruptionStage
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.spawn.SpawnDangerCurve
import net.bestia.worldgen.spawn.SpawnHostility
import net.bestia.worldgen.spawn.SpawnerParams
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.ecs.spawn.WildSpawnConfig
import net.bestia.zone.ecs.spawn.WildSpawnerService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import org.springframework.stereotype.Service
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * What, if anything, stands at each lattice cell.
 *
 * The whole ambient layer's decision-making, and deliberately not an ECS system: it answers a question about
 * the world rather than doing anything to it. [resolve] is `internal` and free of Spring, the world service
 * and the database for the reason `WildSpawnerService.stock` is - it is worth being able to measure every
 * decision in here against a generated world with no tick loop and a hand-built catalogue, which is what
 * `AmbientSpawnDensityTest` buys.
 *
 * ### The order of the rejections is the cost model
 *
 * Cheapest and most selective first, because most cells hold nothing:
 *
 *  1. **Water by biome.** One raster read, and it removes most of the map.
 *  2. **Thinning.** One hash, no world access, and it removes most of the harsh country outright.
 *  3. **Water by height.** `heightAt` against `waterLevelAt`, which covers the ocean, every lake and every
 *     pond in one test - finer than the generator's own kilometre-resolution pair.
 *  4. **Town ring.** A disc test that almost always says "nowhere near a town"; see [TownClearance].
 *  5. **Species.** The catalogue join, the only step that allocates.
 *
 * ### Memoised, because a site cannot change
 *
 * A resolved cell is a pure function of `(worldSeed, cellX, cellY)`, so the cache is a memo and not a cache:
 * nothing to invalidate, no staleness to reason about. Negative answers are kept too, or the ground a player
 * is standing on would be re-rejected four times a second forever. A walking player resolves about a dozen
 * new cells per thirty tiles; the hundred and forty of a cold arrival are paid once.
 */
@Service
class AmbientSiteResolver(
  private val worldService: WorldService,
  private val bestiaCatalogue: BestiaCatalogue,
  private val config: AmbientSpawnConfig,
  private val wildConfig: WildSpawnConfig
) {

  private val resolved = object : LinkedHashMap<Long, AmbientSite?>(1024, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, AmbientSite?>?): Boolean {
      return size > config.siteCacheSize
    }
  }

  private val state by lazy {
    State.of(
      generated = worldService.generated,
      worldSeed = worldService.record.seed,
      catalogue = catalogueOf(bestiaCatalogue.all(), wildConfig),
      config = config,
      wildConfig = wildConfig
    )
  }

  val lattice: AmbientSiteLattice get() = state.lattice

  /** Cells resolved so far, for the debug command. */
  val memoSize: Int get() = resolved.size

  /**
   * The site this cell offers, or null where the wilderness holds nothing.
   *
   * Not thread-safe and does not need to be: the only caller is [AmbientSpawnerSystem] on the tick thread.
   * An access-ordered [LinkedHashMap] mutates on reads, so this must not be shared with a query from
   * elsewhere without a lock.
   */
  fun siteAt(cellX: Long, cellY: Long): AmbientSite? {
    val cell = AmbientSiteLattice.pack(cellX, cellY)
    if (resolved.containsKey(cell)) return resolved[cell]

    val site = resolve(state, cellX, cellY)
    resolved[cell] = site
    return site
  }

  /**
   * Resolves every cell in a window and reports what happened, without disturbing the memo.
   *
   * For the `/ambient` command. Deliberately does **not** write through [resolved]: a diagnostic sweep of a
   * few hundred cells would otherwise evict the ground the player is actually standing on.
   */
  internal fun tallyAround(x: Long, y: Long, windowTiles: Long, tally: Tally): Int {
    val half = windowTiles / 2
    var sites = 0

    for (cellY in state.lattice.cellYOf(y - half)..state.lattice.cellYOf(y + half)) {
      for (cellX in state.lattice.cellXOf(x - half)..state.lattice.cellXOf(x + half)) {
        if (resolve(state, cellX, cellY, tally) != null) sites++
      }
    }
    return sites
  }

  /**
   * Everything the resolver reads, gathered once.
   *
   * Behind a `lazy` in the service because none of it exists until `WorldService` has booted a world, and
   * built from a [GeneratedWorld] rather than from the service so a test can stand one up directly.
   */
  internal class State private constructor(
    val generated: GeneratedWorld,
    val worldSeed: Long,
    val lattice: AmbientSiteLattice,
    val settlements: StandingSettlements,
    val townClearance: TownClearance,
    val catalogue: List<WildSpawnerService.Candidate>,
    val params: SpawnerParams,
    val config: AmbientSpawnConfig,
    val wildConfig: WildSpawnConfig,
    private val homePoints: List<Pair<Double, Double>>,
    private val homeRadius: Double
  ) {
    val surface get() = generated.materializer.surface
    val civilisationDistance: FloatLayer =
      generated.world.layers.require(LayerId.CIVILISATION_DISTANCE)
    val elevation: FloatLayer = generated.world.layers.require(LayerId.ELEVATION)

    /** Inside the ring around a master's arrival point, where the generator keeps everything gentle. */
    fun nearHome(worldX: Double, worldY: Double): Boolean {
      for ((hx, hy) in homePoints) {
        if (hypot(hx - worldX, hy - worldY) <= homeRadius) return true
      }
      return false
    }

    companion object {
      fun of(
        generated: GeneratedWorld,
        worldSeed: Long,
        catalogue: List<WildSpawnerService.Candidate>,
        config: AmbientSpawnConfig,
        wildConfig: WildSpawnConfig
      ): State {
        val params = generated.params.spawner
        val settlements = StandingSettlements.of(generated)
        val homePoints = SettlementSpawnPoints.choose(generated).map { it.position.x to it.position.y }

        LOG.info { "Ambient spawn: ${catalogue.size} placeable species, ${homePoints.size} home ring(s)" }

        return State(
          generated = generated,
          worldSeed = worldSeed,
          lattice = AmbientSiteLattice(worldSeed, config.spacingTiles),
          settlements = settlements,
          townClearance = TownClearance(generated, settlements, config.townClearanceTiles),
          catalogue = catalogue,
          params = params,
          config = config,
          wildConfig = wildConfig,
          homePoints = homePoints,
          homeRadius = params.homeSafeRadius + SettlementSpawnPoints.MAX_ARRIVAL_OFFSET_METRES
        )
      }
    }
  }

  /** Why the cells in some stretch of country came out the way they did. */
  internal class Tally {
    var stocked = 0
      private set
    var water = 0
      private set
    var thinned = 0
      private set
    var town = 0
      private set
    var unstockable = 0
      private set
    val unstockableBiomes = HashSet<String>()

    fun countStocked() {
      stocked++
    }

    fun countWater() {
      water++
    }

    fun countThinned() {
      thinned++
    }

    fun countTown() {
      town++
    }

    fun countUnstockable(biome: Biome) {
      unstockable++
      unstockableBiomes.add(biome.name)
    }

    override fun toString(): String {
      return "stocked=$stocked water=$water thinned=$thinned town=$town " +
          "no-species=$unstockable${if (unstockableBiomes.isEmpty()) "" else " in $unstockableBiomes"}"
    }
  }

  internal companion object {
    private val LOG = KotlinLogging.logger { }

    /** The severity at which the generator switches to its endgame band. */
    private val CORRUPTED_SEVERITY = CorruptionStage.CORRUPTED

    /** The placeable species, with habitats parsed once. Shared with the den layer's own filter. */
    internal fun catalogueOf(
      species: List<net.bestia.zone.bestia.Bestia>,
      wildConfig: WildSpawnConfig
    ): List<WildSpawnerService.Candidate> {
      val excluded = wildConfig.excludedSpecies.toSet()
      return species.filterNot { it.identifier in excluded }.map(WildSpawnerService::Candidate)
    }

    /**
     * The whole decision for one cell, as a pure function. See the class KDoc for the order.
     *
     * [tally] is optional and costs nothing when absent. It exists because "the wilderness is empty here" has
     * four completely different causes with four different fixes - a species nobody authored for this biome,
     * a town, water, or the thinning doing its job - and telling them apart by reading the code is guesswork.
     * `AmbientSpawnDensityTest` and the `/ambient` command both use it.
     */
    internal fun resolve(state: State, cellX: Long, cellY: Long, tally: Tally? = null): AmbientSite? {
      val x = state.lattice.siteX(cellX, cellY)
      val y = state.lattice.siteY(cellX, cellY)
      val worldX = x.toDouble()
      val worldY = y.toDouble()

      val biome = state.surface.biomeAt(worldX, worldY)
      if (biome.isWater) {
        tally?.countWater()
        return null
      }

      // Thinned before anything expensive: one hash, and it removes most of the desert, ice and volcanic
      // ground before it costs a height sample.
      val danger = dangerAt(state, worldX, worldY, biome)
      if (state.lattice.thinningRoll(cellX, cellY) >= keepShare(state, danger)) {
        tally?.countThinned()
        return null
      }

      val elevation = state.generated.base.heightAt(worldX, worldY)
      if (elevation <= state.surface.waterLevelAt(worldX, worldY)) {
        tally?.countWater()
        return null
      }

      if (state.townClearance.blocks(x, y)) {
        tally?.countTown()
        return null
      }

      val severity = state.surface.corruptionAt(worldX, worldY)
      val choice = WildSpawnerService.pick(
        catalogue = state.catalogue,
        worldSeed = state.worldSeed,
        featureId = state.lattice.siteId(cellX, cellY),
        den = facts(state, danger, severity, biome, worldX, worldY),
        config = state.wildConfig
      )
      if (choice == null) {
        // No species the catalogue holds lives in country like this. Missing content, not a broken join.
        tally?.countUnstockable(biome)
        return null
      }

      tally?.countStocked()
      val profile = choice.species.aiProfile
      return AmbientSite(
        cell = AmbientSiteLattice.pack(cellX, cellY),
        bestiaId = choice.species.id,
        position = Vec3L(x, y, ChunkCoords.standingZ(state.generated.config, elevation)),
        throttleable = profile == null || profile !in state.config.neverThrottledProfiles
      )
    }

    /**
     * The level band and habitat facts a site asks the catalogue for.
     *
     * Mirrors `SpawnerStage.marker` so an ambient creature agrees with the den beside it, with two
     * departures that belong to this layer only:
     *
     *  - `boss = false` always. `WildSpawnerService.admits` refuses a boss to a non-boss den, so this is
     *    what makes a raid boss structurally impossible in the baseline population rather than merely
     *    unlikely - and is why nothing here has to special-case one.
     *  - [AmbientSpawnConfig.maxLevel] caps the band. See that field for why.
     */
    private fun facts(
      state: State,
      danger: Double,
      severity: Double,
      biome: Biome,
      worldX: Double,
      worldY: Double
    ): WildSpawnerService.DenFacts {
      val params = state.params
      val corrupted = severity >= CORRUPTED_SEVERITY

      val centre = if (corrupted) {
        lerp(params.corruptedMinLevel.toDouble(), params.maxLevel.toDouble(), 0.5 * severity + 0.5 * danger)
      } else {
        lerp(1.0, params.wildMaxLevel.toDouble(), danger)
      }

      var levelMin = (centre.roundToInt() - params.levelSpread).coerceIn(1, params.maxLevel)
      var levelMax = (centre.roundToInt() + params.levelSpread).coerceIn(1, params.maxLevel)

      // The generator's own home ring. Without it the fields around a starter village would hold level-forty
      // creatures every thirty tiles while its dens stayed gentle, which is worse than the emptiness this
      // layer exists to fix.
      if (state.nearHome(worldX, worldY)) {
        levelMax = minOf(levelMax, params.homeMaxLevel)
        levelMin = minOf(levelMin, levelMax)
      }

      levelMax = minOf(levelMax, state.config.maxLevel)
      levelMin = minOf(levelMin, levelMax)

      return WildSpawnerService.DenFacts(
        levelMin = levelMin,
        levelMax = levelMax,
        biome = biome,
        corrupted = corrupted,
        boss = false,
        temperature = state.surface.temperatureAt(worldX, worldY)
      )
    }

    /**
     * The same curve the dens use, assembled from its parts rather than through `SpawnDangerCurve.of`.
     *
     * Two of the three smooth terms are direct layer samples and the third needs the nearest standing
     * settlement, which is what [StandingSettlements] is for. The reassembled sum can differ from `of` in
     * the last bit, which is fine here and is not inside the generator - see `SpawnDangerCurve`.
     */
    internal fun dangerAt(state: State, worldX: Double, worldY: Double, biome: Biome): Double {
      val params = state.params
      val weights = SpawnDangerCurve.weightSum(params)

      val nearestSettlement = state.settlements.nearestDistance(worldX, worldY)
      val civDistance = state.civilisationDistance.sampleBilinear(worldX, worldY)
      val elevation = state.elevation.sampleBilinear(worldX, worldY) - state.generated.config.seaLevel

      return (
          params.weightCivilisation * SpawnDangerCurve.civilisation(nearestSettlement, params) +
              params.weightRemoteness * SpawnDangerCurve.remoteness(civDistance, params) +
              params.weightRelief * SpawnDangerCurve.relief(elevation, params) +
              params.weightBiome * SpawnHostility.of(biome)
          ) / weights
    }

    /** Share of this cell's sites that survive, given how dangerous the country is. */
    private fun keepShare(state: State, danger: Double): Double {
      return 1.0 - state.config.dangerThinning * danger
    }

    private fun lerp(from: Double, to: Double, t: Double): Double {
      return from + (to - from) * t
    }
  }
}
