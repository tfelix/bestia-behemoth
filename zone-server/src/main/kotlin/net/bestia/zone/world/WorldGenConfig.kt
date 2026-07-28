package net.bestia.zone.world

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * What a world is created *as*, and what to do when the world that exists is not that.
 *
 * These are birth settings, not runtime settings. Once a world row is written, its own dimensions are what
 * the server generates from, because they are what its chunks and any player edits were built against.
 * Changing `width-cells` here and restarting does not quietly reshape the world - it is noticed, and
 * [onMismatch] decides whether that is a refusal or a regeneration.
 *
 * The prefix is `worldgen` rather than `world` because `world` already configures the ECS tick loop.
 *
 * @property seed left unset, a seed is drawn at random and then persisted, so the world is reproducible
 *   afterwards even though it was not chosen. Set it to generate a known world - and note that *changing* an
 *   explicit seed is a request for a different world, which [onMismatch] governs like any other.
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

  /**
   * Whether east and west are the same place, and whether north and south are.
   *
   * A birth setting rather than a runtime one, and it has to be: the ocean margin that hides a seam is forced
   * into the terrain at generation time, and turning a wrap on afterwards would join two coastlines that were
   * never made to meet.
   *
   * Y is not the free choice X is - see [net.bestia.worldgen.core.WorldConfig.wrapY] for what it costs at the
   * seam - but the alternative is a wall, and a wall in a game about walking somewhere is worse.
   */
  val wrapX: Boolean = true,
  val wrapY: Boolean = true,

  /** What to do when the stored world is not the world these settings and this build describe. */
  val onMismatch: OnMismatch = OnMismatch.REFUSE
) {

  /**
   * The three answers to "the world on disk is not the world you asked for".
   *
   * There is no correct default for everyone, which is why this is a setting rather than a rule: the cost of
   * being wrong is a refused boot in one direction and a destroyed world in the other, and which of those is
   * worse depends entirely on whether anybody is playing in it.
   */
  enum class OnMismatch {

    /**
     * Abort the boot. The safe answer and the default.
     *
     * Player edits are deltas over a generated base, so serving a world whose ground no longer matches what
     * they were made against gives buildings floating over new terrain and holes where a floor used to be -
     * and by then the old base is gone, so there is nothing to migrate from. Refusing keeps the world intact
     * and makes the decision a person's.
     */
    REFUSE,

    /**
     * Throw the world away and generate the one that was asked for. **Destructive.**
     *
     * For development, where the settings are the thing being changed and the world is disposable. It
     * deletes the world row and everything derived from it, and puts every master back at the new world's
     * default spawn - their old coordinates describe terrain that no longer exists.
     *
     * Set deliberately. There is no confirmation and no backup.
     */
    REGENERATE,

    /**
     * Boot anyway and log the disagreement.
     *
     * The escape hatch for a mismatch you have reasoned about - a stage version bumped for a change you know
     * cannot move terrain, say. It is not a way to make the message go away; the corruption it permits is
     * silent and permanent.
     */
    IGNORE
  }

  init {
    require(name.isNotBlank()) { "A world needs a name" }
    require(widthCells > 0 && heightCells > 0) { "World extent must be positive" }
    require(cellSizeMetres > 0.0) { "Cell size must be positive" }
    require(chunkSize > 0 && chunkHeight > 0) { "Chunk dimensions must be positive" }
    require(voxelSizeMetres > 0.0) { "Voxel size must be positive" }
  }
}
