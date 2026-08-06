package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.GenRng
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The chunk-volume determinism check: materialise the same chunks twice, concurrently, and compare every byte.
 *
 * `ChunkSeamCheck` is the equivalent for the tier above and stops one level short of the voxels. It compares
 * **`Double` heights, on a single `z`** - `SurfaceSampler` says so in as many words - so nothing in the
 * repository would notice a stale span buffer, a carve that clears a block and leaves its occupancy behind, or
 * a cache in the height field that is not quite thread safe. Each of those is invisible in a heightfield and
 * plainly wrong in a chunk.
 *
 * ### What this compares, and what it does not
 *
 * Byte equality **between two generations of the same chunk**, not between two chunks. Adjacent chunks share no
 * voxels - they own adjacent columns, and the halo that makes the heightfield check possible exists only in
 * `ColumnHeights` - so there is no voxel a pair of neighbours could disagree about. What there is instead is a
 * promise that a chunk is a pure function of the world, and this is that promise stated as an assertion: same
 * inputs, different thread, different order, same bytes. It is a determinism check, and deliberately not a
 * compatibility one - nothing here says a chunk generated today matches one generated last week.
 *
 * So it catches shared mutable state that outlives a call, an index or sampler cache that is not safe under
 * concurrency, and any dependence on the order chunks happen to be built in. It does **not** catch a buffer
 * left stale *within* one chunk pass, because that failure is perfectly deterministic and reproduces
 * identically in both runs. That one needs a test that knows which columns are supposed to be touched.
 *
 * ### Occupancy is compared, and that is half the point
 *
 * A carve writes both arrays, and the failure mode of writing one and not the other is a block of air at full
 * occupancy - which [VoxelChunk.validate] would catch - or, much quieter, a solid block left at the occupancy
 * of the air that used to be there. Comparing materials alone would pass a chunk whose surfaces are all a
 * voxel out.
 *
 * ### Why this is in `voxel/` and its sibling is in `core/`
 *
 * `ChunkSeamCheck` takes a `ChunkColumnSource`, which is a `core` interface, so it belongs there. There is no
 * equivalent abstraction over chunk *volumes* - this needs a [ChunkMaterializer] - and inventing one so the
 * check could live a package up would put a `core` type in the way of every caller for no benefit, or else make
 * `core` depend on `voxel`, which inverts the layering the module's build file states. The check goes where its
 * subject is.
 */
object VoxelSeamCheck {

  /** One voxel two generations of the same chunk disagree about. */
  data class Difference(
    val chunk: ChunkPos,
    val localX: Int,
    val localY: Int,
    val localZ: Int,
    val blockA: Int,
    val blockB: Int,
    val occupancyA: Int,
    val occupancyB: Int
  ) {
    override fun toString() =
      "$chunk ($localX,$localY,$localZ): block $blockA/$blockB occupancy $occupancyA/$occupancyB"
  }

  data class Report(
    val chunksChecked: Int,
    val voxelsCompared: Int,
    /**
     * Voxels that held something other than air, over one of the two generations.
     *
     * The non-vacuity guard. A block of chunks over deep ocean or open sky compares millions of identical
     * zeroes and reports itself clean, which is true and worthless; a reader needs to know the check had
     * something to look at. Complete, tested, and never reached, applied to the harness itself.
     */
    val solidVoxels: Int,
    val differences: List<Difference>
  ) {
    val isClean get() = differences.isEmpty()

    override fun toString() = if (isClean) {
      "VoxelSeamCheck: clean - $chunksChecked chunks, $voxelsCompared voxels agree, $solidVoxels not air"
    } else {
      "VoxelSeamCheck: ${differences.size}/$voxelsCompared voxels differ between two generations, " +
          "first ${differences.first()}"
    }
  }

  /**
   * @param origin lower-left chunk of the block. Choose one with something in it - see [Report.solidVoxels].
   * @param blockSize edge length of the block in chunks. Two is enough: the property is per chunk, and the
   *   cost is a whole chunk volume per generation.
   * @param threads how many materialisations to run concurrently; more than one is the point
   */
  fun run(
    materializer: ChunkMaterializer,
    origin: ChunkPos = ChunkPos(0, 0),
    blockSize: Int = 2,
    threads: Int = 4,
    shuffleSeed: Long = 0x5EA75EEDL
  ): Report {
    require(blockSize >= 1) { "A block of $blockSize chunks has nothing to check" }

    // Every chunk twice, as two independent tasks, so the two generations of one chunk are not adjacent in the
    // queue and stand a fair chance of landing on different threads.
    val tasks = ArrayList<Pair<Int, ChunkPos>>(2 * blockSize * blockSize)
    for (pass in 0..1) {
      for (dy in 0 until blockSize) {
        for (dx in 0 until blockSize) {
          tasks.add(pass to ChunkPos(origin.x + dx, origin.y + dy, origin.z))
        }
      }
    }

    // Shuffled deterministically, exactly as ChunkSeamCheck does it: build order must not matter, so vary it
    // on purpose while keeping the check itself reproducible.
    val rng = GenRng(shuffleSeed)
    for (i in tasks.indices.reversed()) {
      val j = rng.nextInt(i + 1)
      val tmp = tasks[i]
      tasks[i] = tasks[j]
      tasks[j] = tmp
    }

    val pool = Executors.newFixedThreadPool(threads)
    val generated: Map<Pair<Int, ChunkPos>, VoxelChunk> = try {
      pool.invokeAll(
        tasks.map { key ->
          Callable {
            // The slab holding the ground, rather than whichever slab z happens to name: the interesting
            // voxels are the surface, what is built on it and what has been cut out of it, and a check over
            // the bedrock a hundred metres down would be clean for want of anything to be wrong about.
            key to materializer.materializeSurface(key.second.x, key.second.y)
          }
        }
      ).associate { it.get() }
    } finally {
      pool.shutdown()
      pool.awaitTermination(1, TimeUnit.MINUTES)
    }

    val differences = ArrayList<Difference>()
    var compared = 0
    var solid = 0

    val air = BlockType.AIR.id.toByte()

    // A fixed coordinate order, so the report reads the same regardless of thread timing.
    for (dy in 0 until blockSize) {
      for (dx in 0 until blockSize) {
        val chunk = ChunkPos(origin.x + dx, origin.y + dy, origin.z)
        val a = generated.getValue(0 to chunk)
        val b = generated.getValue(1 to chunk)

        // Two runs that anchored on different slabs is itself a disagreement, and comparing voxel for voxel
        // across it would be meaningless rather than merely wrong.
        require(a.chunk == b.chunk) {
          "Two generations of chunk ($dx,$dy) anchored on different slabs: ${a.chunk} and ${b.chunk}"
        }

        for (i in a.blocks.indices) {
          compared++
          if (a.blocks[i] != air) solid++

          if (a.blocks[i] != b.blocks[i] || a.occupancy[i] != b.occupancy[i]) {
            val localZ = i % a.height
            val column = i / a.height
            differences.add(
              Difference(
                chunk = a.chunk,
                localX = column % a.size,
                localY = column / a.size,
                localZ = localZ,
                blockA = a.blocks[i].toInt() and 0xFF,
                blockB = b.blocks[i].toInt() and 0xFF,
                occupancyA = Occupancy.unsigned(a.occupancy[i]),
                occupancyB = Occupancy.unsigned(b.occupancy[i])
              )
            )
          }
        }

        // Free, and it is the assertion the carve most needs: air at a non-zero fill, or a block at zero.
        a.validate()
        b.validate()
      }
    }

    return Report(blockSize * blockSize, compared, solid, differences)
  }
}
