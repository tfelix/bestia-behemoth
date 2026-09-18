package net.bestia.zone.world.ground

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.SMSG
import net.bestia.zone.socket.ChunkFanOut
import net.bestia.zone.world.fire.GroundFireService
import net.bestia.zone.world.stream.ChunkGroundLayersSMSG
import net.bestia.zone.world.stream.ChunkGroundOverlaySMSG
import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Tells clients what has happened to the ground they are holding.
 *
 * ### It keeps its own record of who holds what, and never reads the subscription service
 *
 * The load-bearing decision in the file, and it took two wrong turns to reach.
 *
 * `ChunkSubscriptionService` is tick-thread state owned by `ChunkStreamSystem`, and `SystemScheduler.conflicts`
 * can only compare *components* - read-read does not conflict at all, so a system declaring no components is
 * free to run in a **parallel wave** with it. `ChunkStreamSystem`'s own comment says why `@Order` does not
 * help: *"`@Order` fixes the sequence only among systems that already conflict."* Reading the subscription
 * service from a flush therefore raced with it.
 *
 * The obvious repair - declare a write on a component that system reads, purely to force the ordering - is
 * worse, and measurably so. It makes an always-present system conflict with a large part of the engine,
 * which flattens the wave scheduling for everything, slows every tick, and on this codebase's own test suite
 * ran the heap out.
 *
 * So [holders] is this service's **own** map, maintained only from the subscription callbacks - which run on
 * the tick thread inside `ChunkStreamSystem` itself and are therefore safe by construction. Concurrent maps
 * make a read from another wave safe rather than merely lucky, and the staleness that buys is harmless: the
 * message carries the whole mask and is idempotent, so a newcomer is served by `onChunkSent` and a departed
 * client is sent one message it throws away. Both this and `ScorchRegrowthSystem` can then declare no
 * components at all and mean it.
 *
 * ### One encode per column however many are watching
 *
 * Through [ChunkFanOut], which is its whole contract. Thirty players around one grass fire cost one
 * serialisation between them rather than thirty.
 *
 * ### Newly-served columns come through `onChunkSent`, not `onFirstSubscriber`
 *
 * That is the bug `WorldObjectResidencyService.awaitingBatch` exists to prevent: **the second player to walk
 * into a burnt field gets no first-subscriber callback**, because somebody else already holds that column.
 * Keying on every recipient rather than the first is what tells them about the scar.
 *
 * ### Silence means clean, and that is a deliberate difference from the static-entity batch
 *
 * `ChunkStaticEntitiesSMSG` sends an empty batch for a column with nothing on it, because a client has to know
 * when to stop waiting before it draws. This does **not**, and the asymmetry is the point: almost every column
 * in the world has never burnt and never will, so sending one anyway meant an extra message per chunk per
 * login - a hundred and twenty-odd for a view volume, for ever, to say "nothing happened here". There is also
 * nothing to wait for: un-scorched is what the terrain already draws.
 *
 * An *empty* message is still sent when a column that had something no longer does, because that is the only
 * way a healed scar retires. So the rule is: a column that **changed** is always announced, and a column
 * merely served is announced only if there is something to say.
 */
@Service
class GroundOverlayService(
  private val fanOut: ChunkFanOut,
  private val subscriptions: ChunkSubscriptionService,
  /**
   * `@Lazy`, because the fire marks columns dirty here and this asks the fire for its burning mask - a genuine
   * cycle in the object graph rather than a layering mistake. The two are one subsystem: what is alight, and
   * who has been told about it.
   */
  @Lazy private val fire: GroundFireService,
  /**
   * Every layer that can mark the ground, collected by Spring. This service never learns what any of them
   * means - see [GroundLayerSource]. Empty is legal and means nothing lasting is modelled yet.
   */
  private val layerSources: List<GroundLayerSource>,
) {

  /** Column -> the accounts holding its terrain. This service's own record; see the class note. */
  private val holders = ConcurrentHashMap<Long, MutableSet<Long>>()

  /** Column -> the accounts owed a message about what is alight there. */
  private val owed = ConcurrentHashMap<Long, MutableSet<Long>>()

  /**
   * Column -> the accounts owed a message about its lasting marks.
   *
   * A second set rather than a second service, because [holders] is the hard-won part and one copy of it is
   * the point. Separate from [owed] because the two messages move at different speeds: a fire re-sends what
   * is alight several times a second, and re-sending half a kilobyte of worn cells at that rate for the whole
   * length of a burn is the cost this split exists to avoid.
   */
  private val owedLayers = ConcurrentHashMap<Long, MutableSet<Long>>()

  init {
    subscriptions.onChunkSent { accountId, chunk ->
      val column = ColumnKey.of(chunk.x, chunk.y)
      holders.computeIfAbsent(column) { ConcurrentHashMap.newKeySet() }.add(accountId)

      // Told about the ground only if there is something to say about it - see the class note.
      if (isBurning(chunk.x, chunk.y)) {
        owed.computeIfAbsent(column) { ConcurrentHashMap.newKeySet() }.add(accountId)
      }
      if (hasMarks(column)) {
        owedLayers.computeIfAbsent(column) { ConcurrentHashMap.newKeySet() }.add(accountId)
      }
    }

    // Safe here: a callback runs on the tick thread inside `ChunkStreamSystem`, which is the only writer.
    subscriptions.onLastSubscriber { chunk ->
      val column = ColumnKey.of(chunk.x, chunk.y)
      val remaining = subscriptions.subscribersOfColumn(chunk.x, chunk.y)

      if (remaining.isEmpty()) {
        // Nothing is owed to nobody, and leaving the entries would grow both maps for the whole life of a fire
        // burning in a region every player has walked out of.
        holders.remove(column)
        owed.remove(column)
        owedLayers.remove(column)
      } else {
        holders[column]?.retainAll(remaining)
        owed[column]?.retainAll(remaining)
        owedLayers[column]?.retainAll(remaining)
      }
    }
  }

  val pending get() = owed.size + owedLayers.size

  /**
   * Marks a column's overlay stale for everyone currently holding it.
   *
   * Reads only [holders], so it is safe from any wave. Cheap and idempotent, so a fire may call it once per
   * changed cell.
   */
  fun markDirty(columnKey: Long) {
    val watchers = holders[columnKey] ?: return
    if (watchers.isEmpty()) return

    owed.computeIfAbsent(columnKey) { ConcurrentHashMap.newKeySet() }.addAll(watchers)
  }

  /**
   * Marks a column's lasting marks stale for everyone currently holding it.
   *
   * [markDirty]'s twin, and safe from any wave for the same reason. Per column rather than per layer: the
   * message carries every layer anyway, so knowing which one moved would buy nothing and cost a second index.
   */
  fun markLayersDirty(columnKey: Long) {
    val watchers = holders[columnKey] ?: return
    if (watchers.isEmpty()) return

    owedLayers.computeIfAbsent(columnKey) { ConcurrentHashMap.newKeySet() }.addAll(watchers)
  }

  /**
   * Sends what is owed. Touches no shared state but the fan-out itself.
   *
   * @return how many messages were encoded, which is one per column however many recipients it had
   */
  fun flush(): Int {
    var sent = 0
    sent += drain(owed) { messageFor(it) }
    sent += drain(owedLayers) { layersMessageFor(it) }
    return sent
  }

  private fun drain(queue: ConcurrentHashMap<Long, MutableSet<Long>>, message: (Long) -> SMSG): Int {
    if (queue.isEmpty()) return 0

    var sent = 0
    for ((column, accounts) in queue) {
      if (accounts.isEmpty()) continue
      fanOut.fanOut(accounts, message(column))
      sent++
    }
    queue.clear()

    return sent
  }

  private fun isBurning(chunkX: Int, chunkY: Int): Boolean {
    return fire.burningIn(chunkX, chunkY)?.isEmpty == false
  }

  private fun hasMarks(column: Long): Boolean {
    return layerSources.any { it.nibblesAt(column) != null }
  }

  /**
   * What is alight in one column, never a diff - see the proto.
   *
   * The mask may come back null, and that message is not wasted: it is how a fire that has gone out is
   * retired, since the client replaces a column's overlay outright and an empty one means "nothing here now".
   */
  private fun messageFor(column: Long): ChunkGroundOverlaySMSG {
    val chunkX = ColumnKey.chunkXOf(column)
    val chunkY = ColumnKey.chunkYOf(column)

    return ChunkGroundOverlaySMSG(
      chunk = ChunkPos(chunkX, chunkY, 0),
      burning = fire.burningIn(chunkX, chunkY)?.takeIf { !it.isEmpty }?.toBytes()
    )
  }

  /**
   * Every lasting mark on one column, never a diff.
   *
   * May come back with no layers at all, and that message is not wasted either: it is how a healed scar or a
   * path that has finally faded is retired.
   */
  private fun layersMessageFor(column: Long): ChunkGroundLayersSMSG {
    val cells = layerSources.mapNotNull { source ->
      source.nibblesAt(column)?.let { source.layer to it }
    }.toMap()

    return ChunkGroundLayersSMSG(
      chunk = ChunkPos(ColumnKey.chunkXOf(column), ColumnKey.chunkYOf(column), 0),
      cells = cells
    )
  }
}
