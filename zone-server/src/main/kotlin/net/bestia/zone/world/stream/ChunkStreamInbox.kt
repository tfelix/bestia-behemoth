package net.bestia.zone.world.stream

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ChunkPos
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Carries client-originated chunk work from the Netty threads onto the zone tick thread.
 *
 * Incoming messages are dispatched on the connection's Netty worker thread - `ClientMessageHandler` publishes
 * a Spring event and the listener runs inline - so a handler is *not* on `zone-tick`. Everything the
 * streaming layer owns is documented as single-threaded: `ChunkStore`, `ChunkDelta` and `DerivedStore` all
 * assume one owner, and [ChunkSubscriptionService] holds plain `HashMap`s.
 *
 * So handlers do no work. They enqueue here, and [ChunkStreamSystem] drains this once per tick and does
 * everything on the thread that owns the data. That is cheaper than locking - the alternative would put a
 * lock around the store on the path of every voxel read the server ever makes - and it makes the ordering
 * explicit: a request is served in the tick after it arrived, never halfway through one.
 *
 * ### Bounded, and it drops rather than blocks
 *
 * A queue fed by the network and drained by a fixed budget is a queue a client can grow on purpose. Past
 * [MAX_PENDING] the oldest entries are discarded: a dropped request costs the client one round trip, because
 * the next manifest re-offers whatever it still lacks, whereas an unbounded queue costs the server its heap.
 */
@Service
class ChunkStreamInbox {

  data class Request(val accountId: Long, val chunks: List<ChunkPos>)

  /**
   * Remove rock in a sphere around a world voxel.
   *
   * A radius rather than a block id, because there is no building system: the only terrain mutation the game
   * has is removal, so what a request has to say is *how much* to take, not what to leave behind.
   *
   * The radius is validated on the tick thread rather than here. It is not a permission question - it is a
   * question about what the client's mesher can draw, and the answer lives beside the terrain.
   */
  data class Carve(val accountId: Long, val x: Long, val y: Long, val z: Long, val radius: Double)

  /**
   * Put this account's active entity down at a horizontal position, on whatever the ground there is.
   *
   * No `z`: the point of the command that produces this is to go and look at terrain, and a caller who had to
   * name the elevation would have to know it first. [ChunkStreamSystem] resolves it against the heightfield -
   * which is also why this cannot be done in the chat handler, where the terrain is another thread's to read.
   */
  data class Teleport(val accountId: Long, val x: Long, val y: Long)

  private val requests = ConcurrentLinkedQueue<Request>()
  private val requestCount = AtomicInteger()

  private val carves = ConcurrentLinkedQueue<Carve>()
  private val carveCount = AtomicInteger()

  private val teleports = ConcurrentLinkedQueue<Teleport>()
  private val teleportCount = AtomicInteger()

  val pendingRequests get() = requestCount.get()

  val pendingCarves get() = carveCount.get()

  val pendingTeleports get() = teleportCount.get()

  fun offerRequest(request: Request) {
    if (request.chunks.isEmpty()) return

    requests.add(request)
    trim(requests, requestCount, "chunk requests")
  }

  fun offerCarve(carve: Carve) {
    carves.add(carve)
    trim(carves, carveCount, "debug carves")
  }

  fun offerTeleport(teleport: Teleport) {
    teleports.add(teleport)
    trim(teleports, teleportCount, "teleports")
  }

  fun drainRequests(): List<Request> = drain(requests, requestCount)

  fun drainCarves(): List<Carve> = drain(carves, carveCount)

  fun drainTeleports(): List<Teleport> = drain(teleports, teleportCount)

  /** Called when a connection goes away, so its queued work does not get served to nobody. */
  fun forget(accountId: Long) {
    requestCount.addAndGet(-requests.count { it.accountId == accountId })
    requests.removeIf { it.accountId == accountId }

    carveCount.addAndGet(-carves.count { it.accountId == accountId })
    carves.removeIf { it.accountId == accountId }

    teleportCount.addAndGet(-teleports.count { it.accountId == accountId })
    teleports.removeIf { it.accountId == accountId }
  }

  private fun <T> trim(queue: ConcurrentLinkedQueue<T>, counter: AtomicInteger, what: String) {
    if (counter.incrementAndGet() <= MAX_PENDING) return

    var dropped = 0
    while (counter.get() > MAX_PENDING && queue.poll() != null) {
      counter.decrementAndGet()
      dropped++
    }

    if (dropped > 0) LOG.warn { "Inbox over $MAX_PENDING $what; dropped $dropped oldest" }
  }

  private fun <T> drain(queue: ConcurrentLinkedQueue<T>, counter: AtomicInteger): List<T> {
    if (queue.isEmpty()) return emptyList()

    val drained = ArrayList<T>()
    while (true) {
      val next = queue.poll() ?: break
      counter.decrementAndGet()
      drained.add(next)
    }

    return drained
  }

  private companion object {
    /** Generous enough that a legitimate whole-manifest request never trips it. */
    const val MAX_PENDING = 4096

    private val LOG = KotlinLogging.logger { }
  }
}
