package net.bestia.zone.world

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.Faction
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.store.PipelineVersion
import net.bestia.zone.ecs.script.ScriptComponent
import net.bestia.zone.entity.PersistedEntityRepository
import net.bestia.zone.cartography.chart.MapChartRepository
import net.bestia.zone.entity.deleteAllByKind
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.random.Random

/**
 * Finds the world this server owns, or writes the record for a new one.
 *
 * Separate from [WorldService] so that the database work is a short transaction of its own. Generating the
 * world tier takes seconds, and holding a transaction open across it to save one class would be a poor trade -
 * as well as putting the `@Transactional` boundary on a method called from inside the same bean, where Spring's
 * proxy would not apply it at all.
 */
@Service
class WorldProvisioning(
  private val worldRepository: WorldRepository,
  private val masterSpawnPointRepository: MasterSpawnPointRepository,
  private val persistedEntityRepository: PersistedEntityRepository,
  private val mapChartRepository: MapChartRepository,
  private val config: WorldGenConfig
) {

  /** Whether a world row exists yet. Lets [WorldService] tell a fresh world apart from a pre-existing one. */
  @Transactional(readOnly = true)
  fun exists(): Boolean = worldRepository.count() > 0

  /**
   * The existing world, or a newly created one.
   *
   * Only the *absence* of any world triggers creation. A server whose configured name or dimensions no longer
   * match its stored world keeps the stored world, because the stored one is what its chunks and any player
   * edits were built against - see the note on [WorldGenConfig].
   */
  @Transactional
  fun findOrCreate(): PersistedWorld {
    val existing = worldRepository.findFirstByOrderByIdAsc()
    if (existing != null) {
      LOG.info { "Found world '${existing.name}' (seed ${existing.seed}), created ${existing.createdAt}" }
      return existing
    }

    return create()
  }

  /**
   * Throws the existing world away and writes a new one from the current settings. **Destructive.**
   *
   * Reached from [WorldService] both under [WorldGenConfig.OnMismatch.REGENERATE] and while retrying a fresh
   * world that came out with too few standing settlements. Everything the old world implied - terrain, chunk
   * caches, the cached [MasterSpawnPoint] candidates, any persisted script entities, and in time the stored
   * player deltas over them - goes with the row, because all of it is derived from the seed and dimensions
   * being replaced. Nothing is backed up: a world that was worth keeping should not have been booted under
   * this policy (or, for the retry case, was never shown to a player in the first place).
   */
  @Transactional
  fun recreate(): PersistedWorld {
    val discarded = worldRepository.findFirstByOrderByIdAsc()

    /*
     * Read the outgoing world's victor **before** the delete, and carry it into the world that replaces it.
     *
     * This is the one thing that survives a regeneration, and this line is the whole of the mechanism: the next
     * world's deep history is shaped by whichever Order won this one - see `OrderInfluence` and
     * `HistorySim.swearOrders`. Everything else here is derived from the seed and is thrown away with the row.
     *
     * `discarded.winningOrder` is null until something scores a world, and nothing does yet, so today this
     * carries null forward and every regenerated world gets Genesis' Order-free history. The config fallback is
     * a development lever for looking at the other case - see `WorldGenConfig.previousWinningOrder`.
     */
    val previousWinner = discarded?.winningFaction ?: config.previousWinningFaction

    worldRepository.deleteAll()
    masterSpawnPointRepository.deleteAll()
    // Charts name places by coordinate, and the coordinates mean different terrain in the new world.
    // `MapChart.worldShapeVersion` would catch a survived row and refuse to read it, so this is the tidy half
    // rather than the correctness half - but leaving them would keep an unreadable item in every inventory.
    mapChartRepository.deleteAll()
    persistedEntityRepository.deleteAllByKind(ScriptComponent.KIND)

    // Before the insert, not after the method returns. The name is uniquely indexed and Hibernate is free to
    // order a pending insert ahead of a pending delete inside one transaction, which would collide with the
    // very row being replaced.
    worldRepository.flush()

    LOG.warn { "Discarded world '${discarded?.name}' (seed ${discarded?.seed}) and everything derived from it" }

    return create(previousWinner)
  }

  private fun create(previousWinner: Faction? = config.previousWinningFaction): PersistedWorld {
    val seed = config.seed ?: Random.nextLong()
    if (config.seed == null) {
      // Loudly, because from here on it is permanent: every hill in this world is a consequence of it, and
      // regenerating anything ever again needs this exact number.
      LOG.info { "No seed configured, drew $seed at random. This world is now permanently seeded with it." }
    }

    // The version vector of the pipeline as this build assembles it. Cheap: building the stage graph does not
    // run any of it.
    val worldConfig = config.toWorldConfig(seed)
    // The winner this world is being created *after*, not the config's - they differ on every regeneration, and
    // the version stamped into the row has to be the one the terrain and history are actually built from.
    val versions = PipelineVersion.current(
      StandardWorld.pipeline(worldConfig, config.paramsFor(previousWinner)).pipelineVersion
    )

    val world = worldRepository.save(
      PersistedWorld(
        name = config.name,
        seed = seed,
        widthCells = config.widthCells,
        heightCells = config.heightCells,
        cellSizeMetres = config.cellSizeMetres,
        chunkSize = config.chunkSize,
        chunkHeight = config.chunkHeight,
        voxelSizeMetres = config.voxelSizeMetres,
        seaLevelMetres = config.seaLevelMetres,
        wrapX = config.wrapX,
        wrapY = config.wrapY,
        pipelineVersion = versions.pipelineVersion,
        blockPaletteVersion = versions.blockPaletteVersion,
        chunkFormatVersion = versions.chunkFormatVersion,
        shapeVersion = worldConfig.shapeVersion,
        previousWinningFaction = previousWinner,
        createdAt = Instant.now()
      )
    )

    LOG.info {
      "Created world '${world.name}': seed ${world.seed}, " +
          "${world.widthCells}x${world.heightCells} cells of ${world.cellSizeMetres.toInt()} m " +
          "(${(world.widthMetres / 1000).toInt()}x${(world.heightMetres / 1000).toInt()} km), $versions, " +
          // Logged because it is invisible otherwise: it changes what the *history* of this world says without
          // moving a single voxel, so a world that came out with no Orders in its chronicle looks identical to
          // one that should have had them.
          (previousWinner?.let { won -> "shaped by ${won.label} winning the last world" }
            ?: "no previous victor")
    }

    return world
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
