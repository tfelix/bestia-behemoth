package net.bestia.zone.world

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.store.PipelineVersion
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
  private val config: WorldGenConfig
) {

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
   * Reached only from [WorldService] under [WorldGenConfig.OnMismatch.REGENERATE]. Everything the old world
   * implied - terrain, chunk caches, and in time the stored player deltas over them - goes with the row,
   * because all of it is derived from the seed and dimensions being replaced. Nothing is backed up: a world
   * that was worth keeping should not have been booted under this policy.
   */
  @Transactional
  fun recreate(): PersistedWorld {
    val discarded = worldRepository.findFirstByOrderByIdAsc()

    worldRepository.deleteAll()

    // Before the insert, not after the method returns. The name is uniquely indexed and Hibernate is free to
    // order a pending insert ahead of a pending delete inside one transaction, which would collide with the
    // very row being replaced.
    worldRepository.flush()

    LOG.warn { "Discarded world '${discarded?.name}' (seed ${discarded?.seed}) and everything derived from it" }

    return create()
  }

  private fun create(): PersistedWorld {
    val seed = config.seed ?: Random.nextLong()
    if (config.seed == null) {
      // Loudly, because from here on it is permanent: every hill in this world is a consequence of it, and
      // regenerating anything ever again needs this exact number.
      LOG.info { "No seed configured, drew $seed at random. This world is now permanently seeded with it." }
    }

    // The version vector of the pipeline as this build assembles it. Cheap: building the stage graph does not
    // run any of it.
    val worldConfig = config.toWorldConfig(seed)
    val versions = PipelineVersion.current(StandardWorld.pipeline(worldConfig).pipelineVersion)

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
        createdAt = Instant.now()
      )
    )

    LOG.info {
      "Created world '${world.name}': seed ${world.seed}, " +
          "${world.widthCells}x${world.heightCells} cells of ${world.cellSizeMetres.toInt()} m " +
          "(${(world.widthMetres / 1000).toInt()}x${(world.heightMetres / 1000).toInt()} km), $versions"
    }

    return world
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
