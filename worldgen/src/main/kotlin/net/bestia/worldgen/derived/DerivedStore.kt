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

  /**
   * One chunk's derived structures.
   *
   * [walkable] is eager because something asks for it on nearly every tick: NPC pathing and the player's own
   * move validation both go through it. [summary] and [opacity] are lazy because nothing asks for them at
   * all. Each is a full pass over a chunk's 262 144 voxels, so building all three eagerly had a tracked
   * chunk pay three passes to have one of them read - and a login tracks a whole view volume at once, which
   * put two thirds of the [rebuild] budget into structures with no reader. That is the saving
   * `ChunkStreamConfig.derivedRebuildsPerTick` names as the obvious one to take when this shows up on the
   * tick budget, and it has.
   *
   * Null here means "not built yet", never "no answer": [summaryOf] and [opacityOf] build on demand and
   * cache from then on, exactly as they would have done if a reader had ever existed.
   */
  private class Entry(
    var walkable: WalkableTile,
    var summary: ColumnSummary? = null,
    var opacity: OpacityGrid? = null,
    var stale: Boolean = false
  )

  private val entries = LinkedHashMap<ChunkPos, Entry>()

  /** Insertion-ordered, so rebuilds happen oldest-invalidation-first rather than at random. */
  private val queue = LinkedHashSet<ChunkPos>()

  val trackedChunks get() = entries.size
  val pendingRebuilds get() = queue.size

  /** Chunks currently holding stale structures. Useful in a test, and in a health endpoint. */
  fun staleChunks(): Set<ChunkPos> = queue.toSet()

  /** Built on the first ask rather than with the chunk, then cached - see [Entry]. */
  fun summaryOf(chunk: ChunkPos): ColumnSummary {
    val merged = buildIfAbsent(chunk)
    val entry = entries.getValue(chunk)

    entry.summary?.let { return it }

    return ColumnSummary.of(merged ?: voxels(chunk)).also { entry.summary = it }
  }

  /** Built on the first ask rather than with the chunk, then cached - see [Entry]. */
  fun opacityOf(chunk: ChunkPos): OpacityGrid {
    val merged = buildIfAbsent(chunk)
    val entry = entries.getValue(chunk)

    entry.opacity?.let { return it }

    return OpacityGrid.of(merged ?: voxels(chunk), opacityFactor).also { entry.opacity = it }
  }

  fun walkableOf(chunk: ChunkPos): WalkableTile {
    buildIfAbsent(chunk)

    return entries.getValue(chunk).walkable
  }

  /**
   * True when this chunk's structures are queued and so may not reflect recent edits.
   *
   * Covers a chunk that has never been built at all as well as one whose build went out of date - [queue]
   * means "needs building" for both, and neither has an answer a caller can trust yet.
   */
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
   * Asks for a chunk's structures to be built, out of the ordinary budget, without building them here.
   *
   * **The entry point this class was missing.** Every other way in builds on demand, and the only production
   * callers guard themselves on [isTracked] first - so nothing ever put a chunk into [entries], the queue
   * [rebuild] drains was never filled, and every walkability query in the server answered "unknown" for the
   * whole life of the process. A subsystem that is complete, tested and never *reached* looks exactly like one
   * that works.
   *
   * So the residency has to be pushed in by whoever knows which chunks matter - in `zone-server` that is the
   * chunk subscription set, since a chunk somebody is holding is a chunk something may walk in. Paid for out
   * of [rebuild]'s budget rather than here, because a login subscribes a whole view volume at once and
   * building that inline would be the one hitch on the zone thread this class exists to prevent.
   *
   * Idempotent, and cheap enough to call per arrival: a chunk that is already built is left alone rather than
   * queued for a pointless rebuild.
   */
  fun track(chunk: ChunkPos) {
    if (chunk in entries) return
    queue.add(chunk)
  }

  /**
   * Marks a chunk's structures stale after a delta was applied to it.
   *
   * Only this chunk. Cross-chunk walkability is resolved at query time from two tiles rather than stored,
   * so an edit never invalidates a neighbour - which is what keeps the blast radius of placing one block
   * to exactly one tile.
   *
   * An edit to a chunk nobody is tracking is dropped, deliberately: there is nothing to keep current, and
   * whenever the chunk is tracked it will be built from the merged voxels the delta is already part of.
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

      // A queued chunk may have no entry yet - that is what `track` queues, a first build rather than a
      // rebuild - so an absent entry is inserted rather than skipped. An existing one is updated in place so
      // that anything holding the entry keeps seeing the current structures.
      val merged = voxels(chunk)
      val walkable = WalkableTile.of(merged, agent)
      val entry = entries[chunk]

      if (entry == null) {
        entries[chunk] = Entry(walkable = walkable)
      } else {
        entry.walkable = walkable

        // The lazy parts are dropped rather than rebuilt, and dropped *here* rather than in `invalidate`.
        // Dropping them on invalidation would have the next query rebuild from the edited voxels, which is
        // exactly the answer this class promises not to give until the budget has paid for it.
        entry.summary = null
        entry.opacity = null
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

  /**
   * Builds this chunk's eager structures if it has none, and hands back the voxels it merged to do so.
   *
   * The return value is what keeps a lazy part to one merge rather than two: a first [summaryOf] would
   * otherwise merge once to build the entry and again to build the summary, and the merge is the expensive
   * half of both. `null` means the entry was already there, so a lazy part has to merge for itself.
   */
  private fun buildIfAbsent(chunk: ChunkPos): VoxelChunk? {
    if (chunk in entries) return null

    val merged = voxels(chunk)
    entries[chunk] = Entry(walkable = WalkableTile.of(merged, agent))

    return merged
  }
}
