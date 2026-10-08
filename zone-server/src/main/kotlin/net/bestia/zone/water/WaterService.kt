package net.bestia.zone.water

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.VoxelEdit
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.water.sim.Face
import net.bestia.zone.water.sim.WaterChunk
import net.bestia.zone.water.sim.WaterStepper
import net.bestia.zone.water.sim.WaterVolume
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The water the server moves: what is poured, which chunks the simulation holds, and when its work reaches the
 * world.
 *
 * A tick-thread service like `GroundFireService`: [requestPour] is for a caller with no world scope, such as a chat
 * command on an IO thread, and everything else runs in [step].
 *
 * The simulation works on its own copy of each chunk it holds. Its water reaches [ChunkService] as committed fluid
 * edits: a moving chunk at most every [WaterConfig.commitIntervalSeconds], and every chunk once it settles.
 */
@Service
class WaterService(
  private val chunkService: ChunkService,
  private val config: WaterConfig,
) {

  private class Pour(val x: Long, val y: Long, val z: Long, val radius: Int)

  private val requested = ConcurrentLinkedQueue<Pour>()

  private val volume by lazy { WaterVolume(chunkService::normalise) }
  private val stepper by lazy { WaterStepper(volume) }

  /** Chunks to read in, with the cells to wake once they are. */
  private val pendingLoads = LinkedHashMap<ChunkPos, MutableSet<Int>>()

  /** Held chunks somebody else edited, to read again before the next step. */
  private val stale = LinkedHashSet<ChunkPos>()

  private val lastCommitAt = HashMap<ChunkPos, Double>()
  private var clockSeconds = 0.0

  /** True while this service writes its own edits, so its change listener does not mark them stale. */
  private var committing = false

  private var capReported = false

  init {
    chunkService.onChunkChanged(::onChunkChanged)
  }

  val heldChunks: Int
    get() = volume.size

  /** Nothing to pour, load or move, and every chunk let go of. */
  val isIdle: Boolean
    get() = requested.isEmpty() && pendingLoads.isEmpty() && stale.isEmpty() && volume.size == 0

  /**
   * Asks for a bowl of water around a voxel: the lower half of a sphere, into air only. It is poured on the next
   * step, so nothing is returned.
   */
  fun requestPour(x: Long, y: Long, z: Long, radius: Int) {
    require(radius in 1..MAX_POUR_RADIUS) { "A pour radius must be 1..$MAX_POUR_RADIUS, was $radius" }
    requested.add(Pour(x, y, z, radius))
  }

  /** One step of water. Tick thread only. */
  fun step(deltaSeconds: Float) {
    clockSeconds += deltaSeconds

    drainPours()
    refreshStale()
    loadPending()
    stepper.step(config.cellsPerStep)
    commitDue()
    releaseSettled()
  }

  /** Commits every held chunk's water at once. For shutdown, where nothing may be left behind. */
  fun commitAll() {
    for (chunk in volume.all()) {
      commit(chunk)
    }
  }

  /** @return how many voxels became water */
  internal fun drainPours(): Int {
    var filled = 0

    while (true) {
      val pour = requested.poll() ?: return filled

      for ((pos, edits) in editsOf(pour)) {
        // Commit first, so the poured voxels are not overwritten by the simulation's older view of them.
        volume[pos]?.let(::commit)

        val written = chunkService.editFluid(pos, edits)
        if (written > 0 && volume[pos] == null) {
          pendingLoads.getOrPut(pos) { HashSet() }.addAll(edits.map(VoxelEdit::indexOf))
        }
        filled += written
      }
    }
  }

  private fun editsOf(pour: Pour): Map<ChunkPos, LongArray> {
    val config = chunkService.config
    val radius = pour.radius
    val batches = LinkedHashMap<ChunkPos, MutableSet<Long>>()

    for (dz in -radius..0) {
      for (dy in -radius..radius) {
        for (dx in -radius..radius) {
          if (dx * dx + dy * dy + dz * dz > radius * radius) continue

          val localised = ChunkCoords.localise(config, pour.x + dx, pour.y + dy, pour.z + dz) ?: continue
          val chunk = chunkService.normalise(localised.chunk)
          val index = ChunkCoords.voxelIndex(config, localised.localX, localised.localY, localised.localZ)
          batches.getOrPut(chunk) { HashSet() }.add(VoxelEdit.pack(index, BlockType.WATER, Occupancy.FULL))
        }
      }
    }

    return batches.mapValues { (_, edits) -> edits.sorted().toLongArray() }
  }

  /** Called from inside the chunk store, so it only marks: the store must not be edited again from here. */
  private fun onChunkChanged(pos: ChunkPos) {
    if (committing || volume[pos] == null) return
    stale.add(pos)
  }

  private fun refreshStale() {
    for (pos in stale) {
      val chunk = volume[pos] ?: continue
      commit(chunk)
      chunk.reload(chunkService.merged(pos), chunkService.base(pos))
    }
    stale.clear()
  }

  private fun loadPending() {
    for (pos in volume.wanted) {
      pendingLoads.getOrPut(pos) { HashSet() }
    }
    volume.wanted.clear()

    var loaded = 0
    val iterator = pendingLoads.entries.iterator()
    while (loaded < config.chunkLoadsPerStep && iterator.hasNext()) {
      val (pos, wakes) = iterator.next()
      iterator.remove()

      val held = volume[pos]
      if (held != null) {
        wakes.forEach(held::wake)
        continue
      }

      if (volume.size >= config.maxChunks) {
        reportCap(pos)
        continue
      }

      val chunk = WaterChunk.of(chunkService.merged(pos), chunkService.base(pos))
      volume.add(chunk)
      wakeOnArrival(chunk, wakes)
      loaded++
    }
  }

  /**
   * Wakes the water a newly held chunk brings, and the water beside it that pressed against it while it was not
   * held: that water stopped as if at a wall, and nothing else would move it again.
   */
  private fun wakeOnArrival(chunk: WaterChunk, wakes: Set<Int>) {
    for (index in 0 until chunk.volume) {
      if (chunk.fillAt(index) > 0 && !chunk.isSource(index)) chunk.wake(index)
    }
    wakes.forEach(chunk::wake)

    for (face in Face.entries) {
      val neighbour = chunk.neighbours[face.ordinal] ?: continue

      for (index in 0 until chunk.volume) {
        if (!chunk.crossesBorder(index, face)) continue

        val across = chunk.neighbourIndex(index, face)
        if (neighbour.fillAt(across) > 0 && !neighbour.isSource(across)) neighbour.wake(across)
      }
    }
  }

  private fun commitDue() {
    val due = volume.all()
      .filter { chunk -> chunk.hasUncommittedEdits && (chunk.isAsleep || isMovingAndDue(chunk)) }
      .sortedBy { lastCommitAt[it.pos] ?: Double.NEGATIVE_INFINITY }
      .take(config.commitsPerStep)

    due.forEach(::commit)
  }

  private fun isMovingAndDue(chunk: WaterChunk): Boolean {
    val last = lastCommitAt[chunk.pos] ?: return chunk.hasEditsWorthCommitting
    return chunk.hasEditsWorthCommitting && clockSeconds - last >= config.commitIntervalSeconds
  }

  private fun commit(chunk: WaterChunk) {
    lastCommitAt[chunk.pos] = clockSeconds

    val edits = chunk.takeEdits()
    if (edits.isEmpty()) return

    committing = true
    try {
      chunkService.editFluid(chunk.pos, edits)
    } finally {
      committing = false
    }
  }

  /** Lets go of a settled chunk once nothing beside it can still move water into it. */
  private fun releaseSettled() {
    val settled = volume.all().filter { chunk ->
      chunk.isAsleep && !chunk.hasUncommittedEdits && chunk.neighbours.all { it == null || it.isAsleep }
    }

    for (chunk in settled) {
      volume.remove(chunk.pos)
      lastCommitAt.remove(chunk.pos)
    }
  }

  private fun reportCap(pos: ChunkPos) {
    if (capReported) return
    capReported = true
    LOG.warn { "Water reached $pos but already holds ${config.maxChunks} chunks; it stops there as at a wall" }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** About eight thousand voxels at the cap: plenty to watch, too little to stall a tick. */
    const val MAX_POUR_RADIUS = 16
  }
}
