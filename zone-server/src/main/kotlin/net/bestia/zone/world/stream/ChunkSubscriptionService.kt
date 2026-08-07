package net.bestia.zone.world.stream

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ChunkPos
import org.springframework.stereotype.Service

/**
 * Which chunks each connection has been offered, which it has been sent, and - read the other way round -
 * who is holding any given chunk.
 *
 * The first per-client knowledge set in this codebase. Nothing else tracks what a particular client has been
 * told: entity vanish notifications, for instance, are broadcast to whoever is in range *now* and accept
 * being approximately right. Chunks cannot work that way, for two reasons that pull in the same direction.
 *
 * **A patch is only meaningful to a client that holds the chunk.** Sending edits to someone who never
 * received the base is worse than useless - they cannot apply them and cannot tell that they cannot.
 *
 * **A distance test is wrong at a chunk border, in both directions.** The nearest voxel of a chunk and its
 * farthest are thirty-two metres apart, so any radius either includes chunks a player cannot see or excludes
 * ones they can. The subscription set *is* the answer to "who cares about this chunk", so asking it directly
 * is both cheaper than geometry and exactly right.
 *
 * ### Announced and sent are separate sets
 *
 * [announced] is the authorisation set: what a manifest has offered, and therefore what a
 * [ChunkRequestCMSG] may ask for. [sent] is the subset whose payload has actually gone out, and therefore
 * who a patch must reach. A chunk sits in the gap between them from the moment it is announced until the
 * client asks and is served - which is most of the time, for most chunks, since a client that already holds
 * a revision never asks at all.
 *
 * Not thread safe; lives on the `zone-tick` thread with the rest of the streaming layer.
 */
@Service
class ChunkSubscriptionService {

  private val announced = HashMap<Long, MutableSet<ChunkPos>>()
  private val sent = HashMap<Long, MutableSet<ChunkPos>>()

  /** Reverse index over [sent]: who must be told when this chunk changes. */
  private val subscribers = HashMap<ChunkPos, MutableSet<Long>>()

  /**
   * The same index collapsed to columns: packed `(x, y)` -> account -> how many of its slabs that account
   * holds.
   *
   * A second index rather than a scan, and maintained here rather than by the one caller that wants it,
   * because it is derived from [sent] and [sent] is private. [unsend] is per-account and fires no per-account
   * callback, so a listener outside this class cannot keep an equivalent index correct - it would learn about
   * arrivals through `onChunkSent` and never about departures.
   *
   * The refcount is the point. A column is typically held through three subscribed slabs at once, so a set
   * would drop the account on the first slab that left while two were still held.
   *
   * This is *not* the same question [WorldObjectResidencyService] refcounts columns for. That one is
   * "does this column need to exist at all", which only the consumer can answer because it also has to cancel
   * a release against a re-hold inside one tick. This one is "who has already been told what stands here",
   * which is a pure reverse index over data this class owns.
   */
  private val columnSubscribers = HashMap<Long, MutableMap<Long, Int>>()

  /** Last chunk each player's anchor entity occupied, so a tick can tell whether anything moved. */
  private val anchors = HashMap<Long, ChunkPos>()

  private val firstSubscriber = ArrayList<(ChunkPos) -> Unit>()
  private val lastSubscriber = ArrayList<(ChunkPos) -> Unit>()
  private val chunkSent = ArrayList<(Long, ChunkPos) -> Unit>()

  /**
   * Called when a chunk goes from held by nobody to held by somebody, and back.
   *
   * These exist so that anything scoped to "chunks a client is actually holding" can be driven from the one
   * place that already knows it. [subscribers] is a refcount over every path a chunk can enter or leave a
   * client's view by - login, walking, a `reset` manifest after a teleport, master deselection, disconnect -
   * and all of them funnel through [markSent] and [unsend], so two callbacks cover the lot. A radius test
   * beside this would have to rediscover the refcount to avoid double-loading for overlapping players, and
   * would be wrong at a chunk border in both directions for the reason the class note gives.
   *
   * ### A listener must not do work
   *
   * They fire from inside `ChunkStreamSystem`'s own update, on the tick thread, while systems are iterating -
   * so `World.add` would be deferred to the end of the tick anyway. Mark something and return, the way
   * `ChunkService.onChunkChanged` and `ChunkStreamInbox` already require.
   *
   * ### The address includes z
   *
   * A [ChunkPos] here is one *slab*, so a column with three subscribed slabs fires this three times. A
   * listener that cares about the surface - which is everything about props - has to refcount by column
   * itself. `WorldObjectResidencyService` does.
   */
  fun onFirstSubscriber(listener: (ChunkPos) -> Unit) {
    firstSubscriber.add(listener)
  }

  fun onLastSubscriber(listener: (ChunkPos) -> Unit) {
    lastSubscriber.add(listener)
  }

  /**
   * Called for **every** account that is given a chunk, not only the first.
   *
   * [onFirstSubscriber] answers "does this need to exist", which is a question about the chunk. This answers
   * "who has just been given it", which is a question about one client - and anything that has to follow a
   * chunk payload to its recipient needs the second, because the second player into a wood gets no
   * first-subscriber callback and still has to be told about the trees.
   *
   * Fires after the payload has actually gone out, since [markSent] is only called on a successful write.
   */
  fun onChunkSent(listener: (accountId: Long, chunk: ChunkPos) -> Unit) {
    chunkSent.add(listener)
  }

  val trackedAccounts get() = announced.size

  /** A copy, because callers iterate it while forgetting accounts out of it. */
  fun trackedAccountIds(): List<Long> = announced.keys.toList()

  fun announcedTo(accountId: Long): Set<ChunkPos> = announced[accountId] ?: emptySet()

  fun sentTo(accountId: Long): Set<ChunkPos> = sent[accountId] ?: emptySet()

  /** Accounts that hold this chunk's payload and therefore need its patches. Never a copy - do not mutate. */
  fun subscribersOf(chunk: ChunkPos): Set<Long> = subscribers[chunk] ?: emptySet()

  /**
   * Accounts holding *any* slab of this column, and which have therefore been sent what stands on its surface.
   *
   * The audience for anything scoped to the surface rather than to a slab - a static entity that stopped
   * existing, say. Distinct from [subscribersOf] because a prop belongs to the column: a player in a cave
   * holds a different slab of the same column and was still sent the trees overhead.
   *
   * Not the same set as "players in range". A view volume is eleven chunks across, so a holder can stand
   * 176 m from a prop it was told about; an interest-radius broadcast would leave that client drawing
   * something the server has already forgotten.
   */
  fun subscribersOfColumn(chunkX: Int, chunkY: Int): Set<Long> =
    columnSubscribers[packColumn(chunkX, chunkY)]?.keys ?: emptySet()

  fun isAnnouncedTo(accountId: Long, chunk: ChunkPos): Boolean =
    announced[accountId]?.contains(chunk) == true

  fun anchorOf(accountId: Long): ChunkPos? = anchors[accountId]

  fun setAnchor(accountId: Long, chunk: ChunkPos) {
    anchors[accountId] = chunk
  }

  /**
   * Records a manifest that has just been sent.
   *
   * @param added chunks now on offer
   * @param removed chunks withdrawn; their payloads are dropped from [sent] too, because a client told to
   *   forget a chunk has forgotten it and must not keep receiving patches for it
   * @param reset the manifest replaced the client's whole set rather than amending it
   */
  fun applyManifest(
    accountId: Long,
    added: Collection<ChunkPos>,
    removed: Collection<ChunkPos>,
    reset: Boolean
  ) {
    val announcedSet = announced.getOrPut(accountId) { HashSet() }

    if (reset) {
      // Everything the client held is superseded. Withdraw the lot first so the reverse index cannot keep
      // an account against a chunk that is no longer in its set - a leak that would show up as patches
      // being sent for terrain the client discarded.
      sentTo(accountId).toList().forEach { unsend(accountId, it) }
      announcedSet.clear()
    }

    announcedSet.addAll(added)

    removed.forEach {
      announcedSet.remove(it)
      unsend(accountId, it)
    }
  }

  /** Records that a chunk's payload has gone out, so patches for it now reach this account. */
  fun markSent(accountId: Long, chunk: ChunkPos) {
    sent.getOrPut(accountId) { HashSet() }.add(chunk)

    val holders = subscribers.getOrPut(chunk) { HashSet() }
    val wasUnheld = holders.isEmpty()
    holders.add(accountId)

    val column = columnSubscribers.getOrPut(packColumn(chunk.x, chunk.y)) { HashMap() }
    column[accountId] = (column[accountId] ?: 0) + 1

    if (wasUnheld) firstSubscriber.forEach { it(chunk) }
    chunkSent.forEach { it(accountId, chunk) }
  }

  /**
   * Forgets that this account holds a chunk, so the next patch does not go to it.
   *
   * Called when a chunk leaves the subscription, and when a revision moves past what the client holds by
   * more than a patch can express - a re-send is then the only way to catch it up.
   */
  fun unsend(accountId: Long, chunk: ChunkPos) {
    val wasSent = sent[accountId]?.remove(chunk) == true

    // Guarded on `wasSent` rather than mirroring the `subscribers` bookkeeping below, because unsending a
    // chunk an account never held must not decrement a count it never incremented - `forget` and a `reset`
    // manifest can both reach here for a chunk that was only ever announced.
    if (wasSent) {
      val columnKey = packColumn(chunk.x, chunk.y)
      val column = columnSubscribers[columnKey]
      val held = (column?.get(accountId) ?: 0) - 1

      if (held <= 0) {
        column?.remove(accountId)
        if (column?.isEmpty() == true) columnSubscribers.remove(columnKey)
      } else {
        column?.put(accountId, held)
      }
    }

    val holders = subscribers[chunk] ?: return
    holders.remove(accountId)

    if (holders.isEmpty()) {
      subscribers.remove(chunk)
      lastSubscriber.forEach { it(chunk) }
    }
  }

  /** Drops every trace of a connection. Called on disconnect and when a session is deactivated. */
  fun forget(accountId: Long) {
    sentTo(accountId).toList().forEach { unsend(accountId, it) }

    announced.remove(accountId)
    sent.remove(accountId)
    anchors.remove(accountId)

    LOG.debug { "Forgot chunk subscriptions for account $accountId" }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** Matches `WorldObjectResidencyService`'s own packing, so the two agree on what a column is. */
    fun packColumn(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
  }
}
