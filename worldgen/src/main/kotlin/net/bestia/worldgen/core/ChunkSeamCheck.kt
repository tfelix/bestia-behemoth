package net.bestia.worldgen.core

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The chunk-boundary stress view, as a headless check.
 *
 * Generates a block of chunks independently - on several threads, in a shuffled order - and reports
 * every column where two chunks disagree about the height of the *same world column*. Each chunk is
 * generated with a one-column halo, so a chunk's halo column and its neighbour's first interior
 * column are literally the same place in the world and must produce the same number.
 *
 * Build this before there is anything to stress. Seam bugs are otherwise found by players months
 * later, in the form of a river that jumps sideways at an invisible line, and by then the cause is
 * buried under ten stages.
 */
object ChunkSeamCheck {

  /** One disagreement between two chunks about one world column. */
  data class Seam(
    val chunkA: ChunkPos,
    val chunkB: ChunkPos,
    val worldColumnX: Long,
    val worldColumnY: Long,
    val heightA: Double,
    val heightB: Double
  ) {
    val delta get() = abs(heightA - heightB)

    override fun toString() =
      "column ($worldColumnX,$worldColumnY): $chunkA says $heightA, $chunkB says $heightB " +
          "(delta ${"%.6f".format(delta)})"
  }

  data class Report(
    val chunksChecked: Int,
    val columnsCompared: Int,
    val seams: List<Seam>
  ) {
    val isClean get() = seams.isEmpty()
    val worstDelta get() = seams.maxOfOrNull { it.delta } ?: 0.0

    override fun toString() = if (isClean) {
      "SeamCheck: clean - $chunksChecked chunks, $columnsCompared shared columns agree"
    } else {
      "SeamCheck: ${seams.size}/$columnsCompared shared columns disagree, worst delta $worstDelta"
    }
  }

  /**
   * @param origin lower-left chunk of the block
   * @param blockSize edge length of the block in chunks; the doc's stress view uses 4
   * @param epsilon tolerance in metres. Zero is the right value for a correct pipeline - the two
   *   evaluations run identical code on identical inputs, so they should be bit-identical, not
   *   merely close. Loosen it only for a deliberately approximate stage.
   * @param threads how many chunks to generate concurrently; more than one is the point of the test
   */
  fun run(
    source: ChunkColumnSource,
    origin: ChunkPos = ChunkPos(0, 0),
    blockSize: Int = 4,
    epsilon: Double = 0.0,
    threads: Int = 4,
    shuffleSeed: Long = 0x5EA75EEDL
  ): Report {
    require(blockSize >= 2) { "A block of $blockSize chunks has no shared borders to check" }

    val coords = ArrayList<ChunkPos>(blockSize * blockSize)
    for (dy in 0 until blockSize) {
      for (dx in 0 until blockSize) {
        coords.add(ChunkPos(origin.x + dx, origin.y + dy, origin.z))
      }
    }

    // Shuffle deterministically: the point is that generation order must not matter, so we vary it
    // on purpose while keeping the test itself reproducible.
    val rng = GenRng(shuffleSeed)
    for (i in coords.indices.reversed()) {
      val j = rng.nextInt(i + 1)
      val tmp = coords[i]
      coords[i] = coords[j]
      coords[j] = tmp
    }

    val pool = Executors.newFixedThreadPool(threads)
    val generated: Map<ChunkPos, ColumnHeights> = try {
      val tasks = coords.map { chunk -> Callable { chunk to source.heights(chunk, 1) } }
      pool.invokeAll(tasks).associate { it.get() }
    } finally {
      pool.shutdown()
      pool.awaitTermination(1, TimeUnit.MINUTES)
    }

    val seams = ArrayList<Seam>()
    var compared = 0
    val size = generated.values.first().size

    // Compare in a fixed coordinate order so the report is reproducible regardless of thread timing.
    for (dy in 0 until blockSize) {
      for (dx in 0 until blockSize) {
        val a = ChunkPos(origin.x + dx, origin.y + dy, origin.z)
        val heightsA = generated.getValue(a)

        for (neighbour in sharedColumnsOf(size)) {
          val b = ChunkPos(a.x + neighbour.dx, a.y + neighbour.dy, a.z)
          val heightsB = generated[b] ?: continue

          for (pair in neighbour.columns) {
            val heightA = heightsA[pair.ax, pair.ay]
            val heightB = heightsB[pair.bx, pair.by]
            compared++

            if (abs(heightA - heightB) > epsilon) {
              val (gx, gy) = heightsA.globalColumn(pair.ax, pair.ay)
              seams.add(Seam(a, b, gx, gy, heightA, heightB))
            }
          }
        }
      }
    }

    return Report(generated.size, compared, seams)
  }

  /** Two local columns, in two different chunks, that are the same world column. */
  private data class ColumnPair(val ax: Int, val ay: Int, val bx: Int, val by: Int)

  private data class NeighbourColumns(val dx: Int, val dy: Int, val columns: List<ColumnPair>)

  /**
   * For each of the two forward neighbours (east and north - checking all four would compare every
   * pair twice), the pairs of local columns that refer to the same world column.
   *
   * A chunk's halo column at `size` is its eastern neighbour's interior column `0`.
   */
  private fun sharedColumnsOf(size: Int) = listOf(
    NeighbourColumns(1, 0, (0 until size).map { y -> ColumnPair(size, y, 0, y) }),
    NeighbourColumns(0, 1, (0 until size).map { x -> ColumnPair(x, size, x, 0) })
  )
}
