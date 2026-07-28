package net.bestia.zone.world

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.StageListener
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.store.PipelineVersion
import net.bestia.worldgen.store.VersionGate
import net.bestia.worldgen.voxel.ChunkMaterializer
import net.bestia.zone.BestiaException
import net.bestia.zone.geometry.Vec3L
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

/**
 * Thrown when the running build cannot generate the stored world's terrain.
 *
 * A hard failure on purpose. The alternative is to boot anyway and serve a world whose ground does not match
 * what its player edits were made against, which surfaces as buildings floating over new terrain and holes
 * where somebody's floor used to be - and by then the old base is gone and there is nothing to migrate from.
 */
class IncompatibleWorldException(message: String) : BestiaException(CODE, message) {
  companion object {
    const val CODE = "WORLD_PIPELINE_MISMATCH"
  }
}

/**
 * Thrown when a world's row cannot rebuild the config the world was generated from.
 *
 * Distinct from [IncompatibleWorldException] because the remedy is: that one is a world that no longer
 * matches the build, and regenerating it is a legitimate answer. This is a `WorldConfig` field that decides
 * terrain and has no column in [PersistedWorld], so the stored row silently describes a *different* world -
 * and regenerating would write the same incomplete row again and fail identically on the next boot. It is a
 * bug in this code, not a state the operator can configure their way out of, so no policy applies to it.
 */
class IncompleteWorldRecordException(message: String) : BestiaException(CODE, message) {
  companion object {
    const val CODE = "WORLD_RECORD_INCOMPLETE"
  }
}

/**
 * Published once the terrain has been rebuilt after [WorldGenConfig.OnMismatch.REGENERATE] threw a world away.
 *
 * Anything holding a coordinate into the old world is now holding a coordinate into nothing. An event rather
 * than direct calls because the things that need to hear it - masters, later any persisted structures - are
 * not the world module's to know about, and because it must fire *after* the new terrain exists so a listener
 * can ask where the new spawn is.
 */
data class WorldRecreatedEvent(val world: PersistedWorld)

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
  val record: PersistedWorld get() = require().record

  /** The generated world tier plus the samplers that materialise chunks from it. */
  val generated: GeneratedWorld get() = require().generated

  val config: WorldConfig get() = require().generated.config

  val materializer: ChunkMaterializer get() = require().generated.materializer

  /**
   * Where a player with nowhere else to be starts: the middle of the map, at sea level.
   *
   * The middle rather than the origin, and that is a correctness fix rather than a preference. The generator
   * forces an ocean margin around the world edge - 2.5 km of it for Genesis - and deliberately drowns it several
   * hundred metres deep so the wrap seam has nothing in it worth looking at. The origin is the *corner* of the
   * map, which is inside that margin, so a player starting there begins in featureless deep water with the
   * seabed far below draw distance and no land within walking range.
   *
   * `z` is a placeholder, not an answer. The ground elevation is
   * [net.bestia.zone.world.stream.ChunkService]'s to give and only the tick thread may ask, whereas a master is
   * created on a request thread - so this cannot know how high the ground is and does not try. Sea level is the
   * value it uses, and on a world whose centre is dry land that is hundreds of metres *inside* the terrain: every
   * chunk around the player is uniform rock, which meshes to no surface and renders as a black screen that looks
   * exactly like terrain failing to load.
   *
   * What makes that safe is `ChunkStreamSystem.groundNewcomers`, which snaps any entity carrying no
   * [net.bestia.zone.ecs.movement.Grounded] marker onto the terrain on the first tick it sees it - before the
   * chunk manifest, so the client is never offered the view volume from inside the rock. Anything else that has
   * to invent a position gets the same treatment for free by simply not adding the marker.
   */
  val defaultSpawn: Vec3L
    get() {
      val world = require().generated.config

      // Metres to position units. They coincide at the default voxel size of one metre, but the conversion is
      // what makes that a coincidence rather than an assumption.
      val x = (world.widthMetres / 2.0 / world.voxelSize).toLong()
      val y = (world.heightMetres / 2.0 / world.voxelSize).toLong()

      return Vec3L(x, y, 0)
    }

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
    val generated = StandardWorld.build(config, LoggingStageListener)
    val millis = (System.nanoTime() - startedAt) / 1_000_000

    loaded = Loaded(record, generated)

    LOG.info {
      "World '${record.name}' ready in $millis ms: " +
          "${generated.world.features.all().size} vector features, " +
          "${(record.widthMetres / 1000).toInt()}x${(record.heightMetres / 1000).toInt()} km"
    }

    // After `loaded`, so a listener can ask this service where the new spawn is.
    if (recreated) events.publishEvent(WorldRecreatedEvent(record))
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
    val current = PipelineVersion.current(StandardWorld.pipeline(record.toWorldConfig()).pipelineVersion)
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

  private fun require(): Loaded = loaded
    ?: throw IllegalStateException("The world has not been loaded yet; WorldService.load() runs at boot")

  /** Per-stage timings, so a slow boot can be attributed rather than guessed at. */
  private object LoggingStageListener : StageListener {
    override fun onStageFinish(stage: Stage, region: CellRegion, result: StageResult, millis: Long) {
      LOG.debug { "  ${stage.id.name} in $millis ms" }
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
