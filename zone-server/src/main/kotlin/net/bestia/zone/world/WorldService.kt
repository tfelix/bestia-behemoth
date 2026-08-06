package net.bestia.zone.world

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.worldgen.core.StageListener
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.store.PipelineVersion
import net.bestia.worldgen.store.VersionGate
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

/**
 * Owns the world: its record, and the generated terrain tier built from it.
 *
 * The terrain is **regenerated at boot rather than loaded**, which is worth stating plainly because it looks
 * like a missing feature. Rasters and vector features are a pure function of the seed and the dimensions in
 * [PersistedWorld], so storing them would be storing a cache - and a cache with a correctness risk, since a
 * stale copy is indistinguishable from a fresh one. What players change is stored; what the seed implies is
 * computed. A 128 km world takes about a second.
 *
 * Not thread safe to load; safe to read once loaded, since the world tier is immutable.
 */
@Service
class WorldService(
  private val provisioning: WorldProvisioning,
  private val settings: WorldGenConfig,
  private val events: ApplicationEventPublisher
) {

  private class Loaded(
    val record: PersistedWorld,
    val generated: GeneratedWorld
  )

  @Volatile
  private var loaded: Loaded? = null

  val isLoaded get() = loaded != null

  /** The world's identity and birth settings. */
  val record: PersistedWorld get() = requireLoadedWorld().record

  /** The generated world tier plus the samplers that materialise chunks from it. */
  val generated: GeneratedWorld get() = requireLoadedWorld().generated

  val config: WorldConfig get() = requireLoadedWorld().generated.config

  /**
   * Finds or creates the world record, checks this build can generate it, and builds the terrain.
   *
   * Idempotent: calling it again once loaded does nothing, so a second boot runner or a test that wants the
   * world present cannot accidentally regenerate it.
   *
   * ### Three ways the stored world can be wrong, and they are not the same wrong
   *
   * 1. **The record cannot describe itself.** A terrain-deciding `WorldConfig` field with no column, so the
   *    row rebuilds as a different world than the one it names. Always fatal; see
   *    [IncompleteWorldRecordException] for why no policy can apply.
   * 2. **This build generates different terrain.** A pipeline, palette or format version has moved. Dangerous
   *    - player edits are deltas over a base that no longer exists - so [WorldGenConfig.onMismatch] decides.
   * 3. **The settings ask for a different world.** Someone edited `worldgen` in the configuration. *Not*
   *    dangerous: the row is authoritative and self-consistent, so the running world is still coherent, just
   *    not the one the file describes. Warned about always, acted on only under `REGENERATE` - which is what
   *    that policy is for.
   */
  fun load() {
    if (loaded != null) return

    val alreadyExisted = provisioning.exists()

    var record = provisioning.findOrCreate()
    var recreated = false

    verifyRecordIsComplete(record)

    val drift = record.driftFrom(settings)
    val incompatibility = incompatibilityOf(record)

    if (incompatibility != null || drift.isNotEmpty()) {
      recreated = resolve(record, incompatibility, drift)

      if (recreated) {
        record = provisioning.recreate()

        // The fresh row is checked too. If a field is missing from `PersistedWorld` the new row is as
        // incomplete as the one just deleted, and stopping here is what keeps `REGENERATE` from throwing a
        // world away on every boot for a bug that regenerating cannot fix.
        verifyRecordIsComplete(record)
      }
    }

    val config = record.toWorldConfig()

    val startedAt = System.nanoTime()
    // The *row's* previous victor, never the config's. See `WorldGenConfig.paramsFor`: the tuning has to be a
    // pure function of the stored world, or the boot gate above compares a version nothing generated.
    var generated = StandardWorld.build(
      config, LoggingStageListener, settings.paramsFor(record.previousWinningFaction)
    )
    val millis = (System.nanoTime() - startedAt) / 1_000_000

    if (!alreadyExisted) {
      generated = ensureEnoughSettlements(record, generated) { reroll -> record = reroll }
    } else if (SettlementSpawnPoints.standingSettlementCount(generated) < MIN_STANDING_SETTLEMENTS) {
      LOG.error {
        "!!! World '${record.name}' has fewer than $MIN_STANDING_SETTLEMENTS standing settlements. This " +
            "world already existed before this boot, so it is not being touched - master spawn-point " +
            "selection and anything else that assumes settlements exist may misbehave. !!!"
      }
    }

    loaded = Loaded(record, generated)

    LOG.info {
      "World '${record.name}' ready in $millis ms: " +
          "${generated.world.features.all().size} vector features, " +
          "${(record.widthMetres / 1000).toInt()}x${(record.heightMetres / 1000).toInt()} km"
    }

    // After `loaded`, so a listener can ask this service where the new spawn is. Deliberately not raised
    // for the settlement-count retry loop above: those regenerations happen before anyone has ever seen
    // this world, so there is nothing for a listener to react to.
    if (recreated) events.publishEvent(WorldRecreatedEvent(record))
  }

  /**
   * Guarantees a freshly created world has at least [MIN_STANDING_SETTLEMENTS] standing settlements,
   * per the policy agreed for this feature:
   * - a pinned [WorldGenConfig.seed] means regenerating would produce the exact same world, so this
   *   throws [InsufficientSettlementsException] instead of looping forever;
   * - no pinned seed means a new random seed is drawn and the world rebuilt, up to
   *   [MAX_SETTLEMENT_RETRIES] times, before giving up with the same exception.
   *
   * [onReroll] lets the caller keep its own `record` variable in sync without this method needing to
   * mutate anything outside itself.
   */
  private fun ensureEnoughSettlements(
    initialRecord: PersistedWorld,
    initialGenerated: GeneratedWorld,
    onReroll: (PersistedWorld) -> Unit
  ): GeneratedWorld {
    var record = initialRecord
    var generated = initialGenerated
    var attempt = 0

    while (SettlementSpawnPoints.standingSettlementCount(generated) < MIN_STANDING_SETTLEMENTS) {
      if (settings.seed != null) {
        throw InsufficientSettlementsException(
          "World '${record.name}' (fixed seed ${settings.seed}) has fewer than $MIN_STANDING_SETTLEMENTS " +
              "standing settlements. The seed is pinned in configuration, so regenerating would produce " +
              "the exact same world; change worldgen.seed, or the world's dimensions/density, and try again."
        )
      }

      attempt++
      if (attempt > MAX_SETTLEMENT_RETRIES) {
        throw InsufficientSettlementsException(
          "Could not generate a world with at least $MIN_STANDING_SETTLEMENTS standing settlements after " +
              "$MAX_SETTLEMENT_RETRIES random seeds. This configuration's dimensions/density may not be " +
              "able to support that many settlements."
        )
      }

      LOG.warn {
        "World '${record.name}' (seed ${record.seed}) has fewer than $MIN_STANDING_SETTLEMENTS standing " +
            "settlements; drawing a new random seed and regenerating (attempt $attempt/$MAX_SETTLEMENT_RETRIES)."
      }

      record = provisioning.recreate()
      onReroll(record)
      generated = StandardWorld.build(
        record.toWorldConfig(), LoggingStageListener, settings.paramsFor(record.previousWinningFaction)
      )
    }

    return generated
  }

  /**
   * Applies [WorldGenConfig.onMismatch]. Returns whether the world is to be thrown away and rebuilt.
   *
   * Drift alone never refuses the boot, whatever the policy. The documented contract is that birth settings
   * are ignored once a world exists, and a world that quietly kept its own dimensions is doing exactly what
   * it promised - turning that into a failed start would break running servers over an edit meant for the
   * *next* world. It is still said out loud, because "the config file and the running world disagree" is not
   * a thing anybody should have to discover by measuring the map.
   */
  private fun resolve(record: PersistedWorld, incompatibility: String?, drift: List<String>): Boolean {
    val settingsMoved = drift
      .takeIf { it.isNotEmpty() }
      ?.let { "The configured birth settings ask for a different world: ${it.joinToString("; ")}." }

    val reasons = listOfNotNull(incompatibility, settingsMoved).joinToString(" ")

    if (settings.onMismatch == WorldGenConfig.OnMismatch.REGENERATE) {
      LOG.warn { "Discarding world '${record.name}' and regenerating. $reasons" }
      return true
    }

    if (incompatibility == null) {
      LOG.warn {
        "$reasons The stored world is kept and generation follows it, which is what birth settings mean - " +
            "set worldgen.on-mismatch to REGENERATE to replace it instead, which destroys it."
      }
      return false
    }

    val message = "$reasons Any player edits in this world are deltas over the old base, so regenerating " +
        "against a new one would move the ground under them. Bake the modified chunks before changing the " +
        "pipeline, start a new world, or set worldgen.on-mismatch to REGENERATE to discard this one."

    if (settings.onMismatch == WorldGenConfig.OnMismatch.REFUSE) {
      throw IncompatibleWorldException(message)
    }

    LOG.error { "$message Continuing because worldgen.on-mismatch is IGNORE." }
    return false
  }

  /**
   * Whether this build generates the same terrain the world was born with, and if not, why.
   *
   * Reuses the client/server gate, because the question is identical - two independently generating parties
   * either agree or they do not - and it reports which of the three components disagrees rather than a single
   * useless "incompatible". Here the stored world plays the part of the party that must match.
   */
  private fun incompatibilityOf(record: PersistedWorld): String? {
    val current = PipelineVersion.current(
      // The row's own victor, so this recomputes the version the row was *stamped* with. Reading the config here
      // would report every world as incompatible the moment the development lever was set, which is the exact
      // false positive this check exists to avoid.
      StandardWorld.pipeline(
        record.toWorldConfig(), settings.paramsFor(record.previousWinningFaction)
      ).pipelineVersion
    )
    val stored = PipelineVersion(
      pipelineVersion = record.pipelineVersion,
      blockPaletteVersion = record.blockPaletteVersion,
      chunkFormatVersion = record.chunkFormatVersion
    )

    val verdict = VersionGate.check(server = current, client = stored)
    if (verdict !is VersionGate.Verdict.Incompatible) return null

    return "World '${record.name}' was generated by a different pipeline: ${verdict.reason}. " +
        "Stored $stored, this build $current."
  }

  /**
   * Checks that the row can rebuild the config it was generated from.
   *
   * The hash was computed from the *birth* config and written down; this recomputes it from the columns. They
   * can only disagree if something the generator reads was never stored, in which case it comes back as its
   * default and the terrain is not the terrain this world was made of. That is invisible otherwise - the world
   * generates perfectly, just somewhere else.
   */
  private fun verifyRecordIsComplete(record: PersistedWorld) {
    val rebuilt = record.toWorldConfig().shapeVersion
    if (rebuilt == record.shapeVersion) return

    throw IncompleteWorldRecordException(
      "World '${record.name}' rebuilds to shape $rebuilt but was generated as ${record.shapeVersion}. A " +
          "WorldConfig field that decides terrain has no column in PersistedWorld, so the stored row " +
          "describes a different world than the one it was created from. Add the column and the mapping in " +
          "WorldConfigMapping.kt; regenerating will not fix it."
    )
  }

  private fun requireLoadedWorld(): Loaded = loaded
    ?: throw IllegalStateException("The world has not been loaded yet; WorldService.load() runs at boot")

  /** Per-stage timings, so a slow boot can be attributed rather than guessed at. */
  private object LoggingStageListener : StageListener {
    override fun onStageFinish(stage: Stage, region: CellRegion, result: StageResult, millis: Long) {
      LOG.debug { "  ${stage.id.name} in $millis ms" }
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** A world must have at least this many standing settlements for master spawn-point selection to work. */
    const val MIN_STANDING_SETTLEMENTS = 2

    /** Random reseeds attempted before giving up on [MIN_STANDING_SETTLEMENTS]. */
    const val MAX_SETTLEMENT_RETRIES = 50
  }
}
