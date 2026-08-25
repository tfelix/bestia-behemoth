package net.bestia.zone.world.fire

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.socket.ChunkFanOut
import net.bestia.zone.world.stream.ChunkGroundOverlaySMSG
import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.springframework.stereotype.Service

/**
 * Tells clients what has happened to the ground they are holding.
 *
 * ### Three reasons a column is owed a message, coalesced into one set
 *
 * A column newly served to somebody, a column whose burning cells moved, and a column whose scar changed are
 * all "this column's overlay is stale". Coalescing them into one dirty set per tick means a column with three
 * reasons costs one encode, which matters because a running fire produces all three every step.
 *
 * The newly-served case goes through `onChunkSent` rather than `onFirstSubscriber`, and that is the bug
 * `WorldObjectResidencyService.awaitingBatch` exists to prevent: **the second player to walk into a burnt
 * field gets no first-subscriber callback**, because somebody else is already holding that column. Keying on
 * every recipient rather than the first is what tells them about the scar.
 *
 * ### One encode per column however many are watching
 *
 * Through [ChunkFanOut], which is its whole contract. Thirty players around one grass fire cost one
 * serialisation between them rather than thirty.
 *
 * ### Tick thread only
 *
 * The subscription callbacks fire while systems are iterating, so they only mark - [GroundOverlaySystem]
 * drains. Same division `WorldObjectResidencyService` documents for its own queues.
 */
@Service
class GroundOverlayService(
  private val scorch: ScorchRegistry,
  private val fanOut: ChunkFanOut,
  private val subscriptions: ChunkSubscriptionService,
) {

  /** Columns owed a message. Packed `(x, y)`, the key [ScorchRegistry] uses. */
  private val dirty = LinkedHashSet<Long>()

  /**
   * Columns a client is holding but has not been told the overlay for.
   *
   * Separate from [dirty] because the two are answered differently: a dirty column goes to everyone watching
   * it, and one of these goes to the specific accounts still owed it. Merging them would re-send a whole
   * column's overlay to thirty players because one walked up.
   */
  private val awaiting = HashMap<Long, MutableSet<Long>>()

  init {
    subscriptions.onChunkSent { accountId, chunk ->
      awaiting.getOrPut(ScorchRegistry.columnKeyOf(chunk.x, chunk.y)) { HashSet() }.add(accountId)
    }

    // A column nobody holds is owed nothing: it will be described when somebody next walks up to it, and
    // leaving the entry would keep the set growing for the whole life of a long fire in an empty region.
    subscriptions.onLastSubscriber { chunk ->
      val column = ScorchRegistry.columnKeyOf(chunk.x, chunk.y)
      if (subscriptions.subscribersOfColumn(chunk.x, chunk.y).isEmpty()) {
        awaiting.remove(column)
        dirty.remove(column)
      }
    }
  }

  val pending get() = dirty.size + awaiting.size

  /** Marks a column's overlay stale. Cheap and idempotent, so a fire may call it per changed cell. */
  fun markDirty(columnKey: Long) {
    dirty.add(columnKey)
  }

  /**
   * Sends what is owed.
   *
   * @return how many messages were encoded, which is at most one per column however many recipients each had
   */
  fun flush(): Int {
    if (dirty.isEmpty() && awaiting.isEmpty()) return 0

    var sent = 0

    for (column in dirty) {
      val recipients = subscriptions.subscribersOfColumn(
        ScorchRegistry.chunkXOf(column), ScorchRegistry.chunkYOf(column)
      )
      if (recipients.isEmpty()) continue

      fanOut.fanOut(recipients, messageFor(column))
      sent++
      // Everyone watching has just been told, so a waiter for this column is satisfied by the same message.
      awaiting.remove(column)
    }
    dirty.clear()

    for ((column, accounts) in awaiting) {
      if (accounts.isEmpty()) continue
      fanOut.fanOut(accounts, messageFor(column))
      sent++
    }
    awaiting.clear()

    return sent
  }

  /**
   * The whole truth about one column, never a diff - see the proto. A column with nothing on it still gets a
   * message, because "clean" and "not told yet" are different states to a client deciding whether to draw.
   */
  private fun messageFor(column: Long): ChunkGroundOverlaySMSG {
    val scar = scorch.scarOf(column)

    return ChunkGroundOverlaySMSG(
      chunk = ChunkPos(ScorchRegistry.chunkXOf(column), ScorchRegistry.chunkYOf(column), 0),
      // `visible`, not `mask`: the stored mask is the original burn and a healing scar is smaller than it.
      scorched = scar?.visible?.takeIf { !it.isEmpty }?.toBytes(),
      // Nothing yet - the fire fills this in once it exists.
      burning = null
    )
  }
}
