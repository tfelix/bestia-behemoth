package net.bestia.zone.cartography.tools

import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pipeline.WorldParams
import java.io.File

/**
 * Command line for the map tools: which world to build, and the flags that pick a view of it.
 *
 * Deliberately a second implementation of `viewer/WorldArgs` rather than a reuse of it. `viewer` is the one
 * worldgen package allowed Swing and the filesystem and **nothing may depend on it** - that boundary is what
 * keeps the generator linkable into the server and the client, and it is worth more than the sixty lines
 * saved here. What is shared instead is the *vocabulary*: every flag below is spelled exactly as
 * `buildSrc/WorldGenSettings.FLAGS` emits it, so `-Pgenesis` hands both tool families the same world.
 *
 * Unknown flags are refused rather than ignored. A tool that silently drops `--cells` reports a measurement
 * of the default world as though it were an answer about the one you asked for, which is the bug
 * `worldgen/build.gradle`'s header records having shipped twice.
 */
class MapToolArgs private constructor(
  private val argv: List<String>,
  val config: WorldConfig,
  val params: WorldParams,

  /** Where the tuning came from, for the tool to print. Worth stating: it changes the terrain. */
  val paramsOrigin: String
) {

  fun has(flag: String): Boolean = flag in argv

  /** Last occurrence wins, so `-Pgenesis -Pseed=5` means "the server's world, but seed 5". */
  fun string(flag: String): String? {
    val at = argv.lastIndexOf(flag)
    if (at < 0) return null

    require(at + 1 < argv.size) { "$flag needs a value" }
    return argv[at + 1]
  }

  fun int(flag: String, fallback: Int): Int = string(flag)?.toInt() ?: fallback

  fun long(flag: String, fallback: Long): Long = string(flag)?.toLong() ?: fallback

  fun double(flag: String, fallback: Double): Double = string(flag)?.toDouble() ?: fallback

  fun file(flag: String, fallback: String): File = File(string(flag) ?: fallback)

  companion object {

    private const val SEED = "--seed"
    private const val WIDTH_CELLS = "--width-cells"
    private const val HEIGHT_CELLS = "--height-cells"
    private const val CELL_SIZE = "--cell-size"
    private const val CHUNK_SIZE = "--chunk-size"
    private const val CHUNK_HEIGHT = "--chunk-height"
    private const val VOXEL_SIZE = "--voxel-size"
    private const val SEA_LEVEL = "--sea-level"
    private const val WRAP_X = "--wrap-x"
    private const val WRAP_Y = "--wrap-y"
    private const val PARAMS = "--params"

    /**
     * Flags that shape the world, kept complete against `WorldGenSettings.FLAGS`.
     *
     * A tool-specific flag list is passed in by each `main`, so a typo in `--levl` fails the run instead of
     * quietly rendering level zero.
     */
    private val WORLD_FLAGS = setOf(
      SEED, WIDTH_CELLS, HEIGHT_CELLS, CELL_SIZE, CHUNK_SIZE, CHUNK_HEIGHT,
      VOXEL_SIZE, SEA_LEVEL, WRAP_X, WRAP_Y, PARAMS
    )

    fun parse(argv: Array<String>, toolFlags: Set<String>): MapToolArgs {
      val args = argv.toList()
      reject(args, WORLD_FLAGS + toolFlags)

      val defaults = StandardWorld.demoConfig()
      val cellSize = value(args, CELL_SIZE)?.toDouble()

      val config = WorldConfig(
        seed = value(args, SEED)?.toLong() ?: StandardWorld.DEFAULT_SEED,
        widthCells = value(args, WIDTH_CELLS)?.toInt() ?: defaults.widthCells,
        heightCells = value(args, HEIGHT_CELLS)?.toInt() ?: defaults.heightCells,
        baseResolution = if (cellSize != null) Resolution(cellSize) else defaults.baseResolution,
        seaLevel = value(args, SEA_LEVEL)?.toDouble() ?: defaults.seaLevel,
        chunkSize = value(args, CHUNK_SIZE)?.toInt() ?: defaults.chunkSize,
        chunkHeight = value(args, CHUNK_HEIGHT)?.toInt() ?: defaults.chunkHeight,
        voxelSize = value(args, VOXEL_SIZE)?.toDouble() ?: defaults.voxelSize,
        wrapX = value(args, WRAP_X)?.toBooleanStrict() ?: defaults.wrapX,
        wrapY = value(args, WRAP_Y)?.toBooleanStrict() ?: defaults.wrapY
      )

      val paramsPath = value(args, PARAMS)
      val params: WorldParams
      val origin: String
      if (paramsPath == null) {
        params = WorldParams.DEFAULT
        origin = "defaults"
      } else {
        // ParamsText.parse takes text rather than a path precisely because worldgen may not open a file.
        // The tool is in zone-server, so the read happens here.
        val file = File(paramsPath)
        require(file.isFile) { "No params file at ${file.absolutePath}" }

        params = WorldParams.load(ParamsText.parse(file.readText(), paramsPath), WorldParams.DEFAULT)
        origin = paramsPath
      }

      return MapToolArgs(args, config, params, origin)
    }

    private fun value(args: List<String>, flag: String): String? {
      val at = args.lastIndexOf(flag)
      if (at < 0) return null

      require(at + 1 < args.size) { "$flag needs a value" }
      return args[at + 1]
    }

    private fun reject(args: List<String>, known: Set<String>) {
      val unknown = args.filter { it.startsWith("--") && it !in known }
      require(unknown.isEmpty()) {
        "Unknown flag(s) ${unknown.joinToString(", ")}. Known: ${known.sorted().joinToString(", ")}"
      }
    }
  }
}
