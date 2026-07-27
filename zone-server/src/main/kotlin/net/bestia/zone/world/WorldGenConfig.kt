package net.bestia.zone.world

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How a *new* world is created. Ignored for a world that already exists.
 *
 * Worth being explicit about that: these are birth settings, not runtime settings. Once a world row is written,
 * its own dimensions are what the server generates from, because they are what its chunks and any player edits
 * were built against. Changing `width-cells` here and restarting gives a new world only if the database is
 * empty - otherwise it changes nothing at all, which is the safe direction for a setting to fail in.
 *
 * The prefix is `worldgen` rather than `world` because `world` already configures the ECS tick loop.
 *
 * @property seed left unset, a seed is drawn at random and then persisted, so the world is reproducible
 *   afterwards even though it was not chosen. Set it to generate a known world.
 * @property refuseOnPipelineMismatch whether to abort the boot when the stored pipeline version disagrees with
 *   this build's. On by default; see [WorldService] for why the alternative is silent corruption.
 */
@ConfigurationProperties(prefix = "worldgen")
data class WorldGenConfig(

  /** Name given to the world if none exists yet. */
  val name: String = PersistedWorld.GENESIS,

  val seed: Long? = null,

  /** World extent in cells. At the default cell size a cell is a kilometre, so this is kilometres. */
  val widthCells: Int = 128,
  val heightCells: Int = 128,

  val cellSizeMetres: Double = 1_000.0,

  val chunkSize: Int = 32,
  val chunkHeight: Int = 256,
  val voxelSizeMetres: Double = 1.0,
  val seaLevelMetres: Double = 0.0,

  val refuseOnPipelineMismatch: Boolean = true
) {

  init {
    require(name.isNotBlank()) { "A world needs a name" }
    require(widthCells > 0 && heightCells > 0) { "World extent must be positive" }
    require(cellSizeMetres > 0.0) { "Cell size must be positive" }
    require(chunkSize > 0 && chunkHeight > 0) { "Chunk dimensions must be positive" }
    require(voxelSizeMetres > 0.0) { "Voxel size must be positive" }
  }
}
