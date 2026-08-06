package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.VoxelChunk

/**
 * Holds the derived structures for the chunks a node owns, and keeps them current as deltas arrive.
 *
 * The pattern is the same for all of them: cheap to query, incrementally updatable, rebuilt from voxels
 * only on invalidation. What makes it work in a live zone is the *budget*.
 *
 * When a player places a block, the affected tile is marked stale and queued - it is not rebuilt there and
 * then. [rebuild] is called from the zone loop with a budget, and does that many rebuilds and no more.
 * Queries in the meantime return the stale structure. That is a deliberate trade with a clear winner: an
 * NPC walking through a doorway that closed two hundred milliseconds ago is an artefact nobody files a bug
 * about, while a forty millisecond hitch on the zone thread every time somebody places a fence is one
 * everybody does.
 *
 * Not thread safe. A chunk has one owning node and derived state follows ownership.
 */
class DerivedStore(
  /** Produces the *merged* voxels for a chunk: generated base with any delta already applied. */
  private val voxels: (ChunkPos) -> VoxelChunk,
  private val agent: AgentProfile = AgentProfile(),
  private val opacityFactor: Int = OpacityGrid.DEFAULT_FACTOR
) {

  private class Entry(
    var summary: ColumnSummary,
    var opacity: OpacityGrid,
    var walkable: WalkableTile,
    var stale: Boolean = false
  )

  private val entries = LinkedHashMap<ChunkPos, Entry>()

  /** Insertion-ordered, so rebuilds happen oldest-invalidation-first rather than at random. */
  private val queue = LinkedHashSet<ChunkPos>()

  val trackedChunks get() = entries.size
  val pendingRebuilds get() = queue.size

  /** Chunks currently holding stale structures. Useful in a test, and in a health endpoint. */
  fun staleChunks(): Set<ChunkPos> = queue.toSet()

  fun summaryOf(chunk: ChunkPos): ColumnSummary = entryOf(chunk).summary

  fun opacityOf(chunk: ChunkPos): OpacityGrid = entryOf(chunk).opacity

  fun walkableOf(chunk: ChunkPos): WalkableTile = entryOf(chunk).walkable

  /** True when this chunk's structures are queued for a rebuild and may not reflect recent edits. */
  fun isStale(chunk: ChunkPos) = chunk in queue

  /**
   * Whether this chunk's structures are already built, so asking about it is a lookup rather than a rebuild.
   *
   * Every other query here builds on demand, which is right for a caller that needs an answer and wrong for
   * one that would rather skip the column: a pathfinder expanding into unloaded country would materialise
   * half a megabyte of voxels per step, and it has no business generating the world as a side effect of
   * deciding where to walk. This lets it ask what is cheap and treat the rest as unknown.
   */
  fun isTracked(chunk: ChunkPos) = chunk in entries

  /**
   * Marks a chunk's structures stale after a delta was applied to it.
   *
   * Only this chunk. Cross-chunk walkability is resolved at query time from two tiles rather than stored,
   * so an edit never invalidates a neighbour - which is what keeps the blast radius of placing one block
   * to exactly one tile.
   */
  fun invalidate(chunk: ChunkPos) {
    val entry = entries[chunk] ?: return
    entry.stale = true
    queue.add(chunk)
  }

  fun forget(chunk: ChunkPos) {
    entries.remove(chunk)
    queue.remove(chunk)
  }

  /**
   * Rebuilds up to [budget] queued chunks.
   *
   * @return how many were rebuilt, so a caller can tell whether it is keeping up
   */
  fun rebuild(budget: Int = 1): Int {
    require(budget >= 0) { "budget must not be negative, was $budget" }

    var done = 0
    val iterator = queue.iterator()
    while (done < budget && iterator.hasNext()) {
      val chunk = iterator.next()
      iterator.remove()

      // `invalidate`/`forget` keep `entries` and `queue` in lockstep, so a queued chunk always has an entry;
      // guarded rather than asserted because a dequeued chunk that turned out to have none should not count
      // as rebuilt.
      val entry = entries[chunk] ?: continue
      build(chunk).let {
        entry.summary = it.summary
        entry.opacity = it.opacity
        entry.walkable = it.walkable
        entry.stale = false
      }
      done++
    }

    return done
  }

  /** Rebuilds everything queued. For tests and for world load, never for the zone loop. */
  fun rebuildAll(): Int = rebuild(queue.size)

  /**
   * Whether a step from one column to a neighbouring column is possible, across a chunk border if need be.
   *
   * The query that the "do not store links" decision exists to serve: it reads two tiles and subtracts two
   * numbers, and it never needed either tile to know about the other when it was built.
   *
   * @param fromSurface the surface height being stepped off, in voxel units - see [WalkableTile]
   */
  fun canStep(
    from: ChunkPos,
    fromLocalX: Int,
    fromLocalY: Int,
    fromSurface: Double,
    to: ChunkPos,
    toLocalX: Int,
    toLocalY: Int
  ): Boolean {
    val target = walkableOf(to).stepTarget(toLocalX, toLocalY, fromSurface)
    if (target < 0.0) return false
    return walkableOf(from).isWalkable(fromLocalX, fromLocalY, ColumnSummary.voxelOf(fromSurface))
  }

  private fun entryOf(chunk: ChunkPos): Entry = entries.getOrPut(chunk) { build(chunk) }

  private fun build(chunk: ChunkPos): Entry {
    val merged = voxels(chunk)
    return Entry(
      summary = ColumnSummary.of(merged),
      opacity = OpacityGrid.of(merged, opacityFactor),
      walkable = WalkableTile.of(merged, agent)
    )
  }
}
