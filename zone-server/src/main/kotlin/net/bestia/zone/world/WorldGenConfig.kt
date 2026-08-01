package net.bestia.zone.world

import net.bestia.worldgen.pipeline.WorldParams
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
 *   explicit seed is a request for a different world, which [onMismatch] governs like any other. The shipped
 *   `application.yml` pins it: the development datasource is in-memory, so an unpinned seed makes every restart
 *   a different planet and no terrain bug reproduces twice.
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

  /**
   * The generator tuning this server builds with: the two hundred-odd numbers that are not [WorldConfig] fields.
   *
   * **One value, read by every call site**, and that is the whole reason it is here rather than defaulted at
   * each of them. Five places construct or version the pipeline - [WorldProvisioning.create] stamps the birth
   * version into the row, [WorldService.incompatibilityOf] recomputes it to compare, and three build the world -
   * and while every one of them let `params` default they agreed for free. The moment they do not, the boot gate
   * compares a `pipelineVersion` that **nothing generated**: the row would hold the version of one tuning, the
   * terrain would be built from another, and the check that exists to catch exactly that would pass.
   *
   * Not yet configurable, deliberately. Reading a params file means a path in `application.yml` and a decision
   * about what happens when it changes under a live world, which is server work rather than generator work. What
   * this closes now is the structural hole: when that arrives it is set *here*, once, and every consumer already
   * reads it.
   */
  val params: WorldParams = WorldParams.DEFAULT

  init {
    require(name.isNotBlank()) { "A world needs a name" }
    require(widthCells > 0 && heightCells > 0) { "World extent must be positive" }
    require(cellSizeMetres > 0.0) { "Cell size must be positive" }
    require(chunkSize > 0 && chunkHeight > 0) { "Chunk dimensions must be positive" }
    require(voxelSizeMetres > 0.0) { "Voxel size must be positive" }
  }
}
