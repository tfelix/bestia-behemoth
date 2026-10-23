package net.bestia.zone.world.residency

import net.bestia.worldgen.core.ChunkPos
import org.springframework.stereotype.Service

/**
 * Which chunks the server simulates: walk data and ground marks exist only for held chunks.
 *
 * A refcount over every holder - the clients' subscriptions count as one holder, each simulation anchor as
 * another - so ground somebody walks on stays resident whether or not a client is looking at it. The
 * listeners must only mark work and return; they fire while systems iterate.
 *
 * Not thread safe; lives on the `zone-tick` thread with the streaming layer.
 */
@Service
class ChunkResidency {

  private val holds = HashMap<ChunkPos, Int>()

  /** Holds per column, summed over its slabs: surface state belongs to the column, not to one slab. */
  private val columnHolds = HashMap<Long, Int>()

  private val firstHolder = ArrayList<(ChunkPos) -> Unit>()
  private val lastHolder = ArrayList<(ChunkPos) -> Unit>()

  fun onFirstHolder(listener: (ChunkPos) -> Unit) {
    firstHolder.add(listener)
  }

  fun onLastHolder(listener: (ChunkPos) -> Unit) {
    lastHolder.add(listener)
  }

  val residentChunks: Int
    get() {
      return holds.size
    }

  val residentColumns: Int
    get() {
      return columnHolds.size
    }

  fun isResident(chunk: ChunkPos): Boolean {
    return chunk in holds
  }

  fun isColumnResident(chunkX: Int, chunkY: Int): Boolean {
    return packColumn(chunkX, chunkY) in columnHolds
  }

  fun hold(chunk: ChunkPos) {
    val count = (holds[chunk] ?: 0) + 1
    holds[chunk] = count
    columnHolds.merge(packColumn(chunk.x, chunk.y), 1, Int::plus)

    if (count == 1) firstHolder.forEach { it(chunk) }
  }

  /** Releasing a chunk that is not held is a caller bug, and is refused rather than allowed to go negative. */
  fun release(chunk: ChunkPos) {
    val count = holds[chunk] ?: error("Released chunk $chunk, which nobody holds")

    if (count > 1) {
      holds[chunk] = count - 1
    } else {
      holds.remove(chunk)
    }

    val column = packColumn(chunk.x, chunk.y)
    val columnCount = columnHolds.getValue(column)
    if (columnCount > 1) columnHolds[column] = columnCount - 1 else columnHolds.remove(column)

    if (count == 1) lastHolder.forEach { it(chunk) }
  }

  private companion object {
    fun packColumn(x: Int, y: Int): Long {
      return (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    }
  }
}
