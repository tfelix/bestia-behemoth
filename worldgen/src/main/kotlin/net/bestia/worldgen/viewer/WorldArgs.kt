package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.WorldConfig
import java.util.Locale

/**
 * The command line the offline tools share: which world to run on, and nothing else.
 *
 * One class rather than a `valueOf` helper per `main`, for two reasons that have both already cost something.
 *
 * **Unknown flags were ignored.** `-Pcells=128` was accepted by a task that did not forward it and the tool
 * generated the default world instead, so a measurement taken that way looked like a real answer about a small
 * world and was an answer about a large one. Every flag here is checked against a declared set and an unknown
 * one fails.
 *
 * **The flags could not express a real world.** They covered the seed and the extent, which is two of the
 * eleven [WorldConfig] fields that decide terrain - so the viewer structurally could not be pointed at the
 * world `zone-server` boots, whose `wrapY` alone moves every coastline. The set below is complete against
 * [WorldConfig.shapeVersion], which is the list of things that make two worlds different worlds.
 *
 * @see net.bestia.worldgen.viewer.ViewerMain for the interactive entry point
 */
class WorldArgs(
  private val args: List<String>,

  /** Flags the calling tool understands that say nothing about which world it is - `--export`, `--span`. */
  extraFlags: Set<String> = emptySet()
) {

  init {
    val known = WORLD_FLAGS + extraFlags
    val unknown = args.filter { it.startsWith("--") && it !in known }

    require(unknown.isEmpty()) {
      "Unknown flag ${unknown.joinToString(" ")}. Known: ${known.sorted().joinToString(" ")}"
    }

    require(!(has(CELLS) && (has(WIDTH_CELLS) || has(HEIGHT_CELLS)))) {
      "$CELLS sets both edges, so passing it with $WIDTH_CELLS or $HEIGHT_CELLS is a contradiction - pick one"
    }
  }

  /**
   * The value of [flag], or null when it is absent.
   *
   * Last occurrence wins, and that is what makes layering work: `-Pgenesis -Pseed=7` forwards the world's own
   * settings and then overrides one of them, so the override has to be the one that lands.
   */
  fun value(flag: String): String? {
    val i = args.lastIndexOf(flag)
    if (i < 0) return null

    require(i + 1 < args.size && !args[i + 1].startsWith("--")) { "$flag was given without a value" }
    return args[i + 1]
  }

  fun has(flag: String) = flag in args

  fun int(flag: String) = value(flag)?.let { it.toIntOrNull() ?: reject(flag, it, "a whole number") }

  fun long(flag: String) = value(flag)?.let { it.toLongOrNull() ?: reject(flag, it, "a whole number") }

  fun double(flag: String) = value(flag)?.let { it.toDoubleOrNull() ?: reject(flag, it, "a number") }

  fun boolean(flag: String) =
    value(flag)?.let { it.toBooleanStrictOrNull() ?: reject(flag, it, "true or false") }

  /**
   * [base] with every world flag that was given applied to it.
   *
   * Applied as overrides on a base rather than assembled from scratch so that each tool keeps its own idea of
   * a sensible default world - the viewer's is big enough to show a river network, the probe's is small enough
   * to build in under a second - while both can be pointed at any world at all.
   */
  fun worldConfig(base: WorldConfig): WorldConfig {
    var config = base

    long(SEED)?.let { config = config.copy(seed = it) }

    // Before the per-edge flags, which are the more specific statement. The two cannot both be present
    // anyway; this only decides which wins if that check is ever relaxed.
    int(CELLS)?.let { config = config.copy(widthCells = it, heightCells = it) }
    int(WIDTH_CELLS)?.let { config = config.copy(widthCells = it) }
    int(HEIGHT_CELLS)?.let { config = config.copy(heightCells = it) }

    double(CELL_SIZE)?.let { config = config.copy(baseResolution = Resolution(it)) }
    double(SEA_LEVEL)?.let { config = config.copy(seaLevel = it) }
    int(CHUNK_SIZE)?.let { config = config.copy(chunkSize = it) }
    int(CHUNK_HEIGHT)?.let { config = config.copy(chunkHeight = it) }
    double(VOXEL_SIZE)?.let { config = config.copy(voxelSize = it) }
    boolean(WRAP_X)?.let { config = config.copy(wrapX = it) }
    boolean(WRAP_Y)?.let { config = config.copy(wrapY = it) }
    double(DETAIL_SCALE)?.let { config = config.copy(detailScaleOverride = it) }
    double(OCEAN_BORDER)?.let { config = config.copy(oceanBorderOverride = it) }

    return config
  }

  private fun reject(flag: String, raw: String, expected: String): Nothing =
    throw IllegalArgumentException("$flag expects $expected, got '$raw'")

  companion object {

    const val SEED = "--seed"

    /** Both edges at once. The common case, and the only extent flag the tools had. */
    const val CELLS = "--cells"

    const val WIDTH_CELLS = "--width-cells"
    const val HEIGHT_CELLS = "--height-cells"
    const val CELL_SIZE = "--cell-size"
    const val SEA_LEVEL = "--sea-level"
    const val CHUNK_SIZE = "--chunk-size"
    const val CHUNK_HEIGHT = "--chunk-height"
    const val VOXEL_SIZE = "--voxel-size"
    const val WRAP_X = "--wrap-x"
    const val WRAP_Y = "--wrap-y"
    const val DETAIL_SCALE = "--detail-scale"
    const val OCEAN_BORDER = "--ocean-border"

    /**
     * Every flag that changes what the terrain is.
     *
     * Must stay complete against [WorldConfig.shapeVersion]. A field in that hash with no flag here is a
     * world the offline tools cannot be pointed at, which is how the viewer came to be unable to show the
     * one the server runs.
     */
    val WORLD_FLAGS = setOf(
      SEED, CELLS, WIDTH_CELLS, HEIGHT_CELLS, CELL_SIZE, SEA_LEVEL,
      CHUNK_SIZE, CHUNK_HEIGHT, VOXEL_SIZE, WRAP_X, WRAP_Y, DETAIL_SCALE, OCEAN_BORDER
    )

    /**
     * One line naming the world, for the console and the window title.
     *
     * Spells out the two derived numbers as well as the given ones. Both are computed from the extent rather
     * than configured, so they change under you when the extent does, and a world that came out with no
     * rivers is usually a detail scale you did not know you had.
     */
    fun summary(config: WorldConfig): String = buildString {
      append("${config.widthCells}x${config.heightCells} cells")
      append(" of ${config.baseResolution.metresPerCell.toInt()} m")
      append(", seed ${config.seed}")
      // Locale.ROOT: this line is read next to a configuration file that writes 4.0, and a decimal comma
      // in a diagnostic invites the reader to wonder whether it is a different number.
      append(", detail scale ${"%.2f".format(Locale.ROOT, config.detailScale)}")
      append(", ocean margin ${config.oceanBorderMetres.toInt()} m")
      append(", wrap ")
      append(
        when {
          config.wrapX && config.wrapY -> "x+y"
          config.wrapX -> "x"
          config.wrapY -> "y"
          else -> "none"
        }
      )
    }

    /** A short name for the window title and the status line. */
    fun label(config: WorldConfig) =
      "seed ${config.seed}, ${config.widthCells}x${config.heightCells}"
  }
}
