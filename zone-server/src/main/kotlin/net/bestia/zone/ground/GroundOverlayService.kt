package net.bestia.zone.ground

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.ChunkFanOut
import net.bestia.zone.ground.fire.GroundFireService
import net.bestia.zone.world.stream.ChunkSubscriptionService
import net.bestia.zone.world.time.BestiaClock
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Tells clients what has happened to the ground they are holding.
 *
 * ### It keeps its own record of who holds what, and never reads the subscription service
 *
 * `ChunkSubscriptionService` is tick-thread state owned by `ChunkStreamSystem`, and `ScorchRegrowthSystem` reads
 * this service too, from another phase. So [holders] is this service's **own** map, maintained only from the subscription callbacks - which run on
 * the tick thread inside `ChunkStreamSystem` itself and are therefore safe by construction. Concurrent maps
 * make a read from another wave safe rather than merely lucky, and the staleness that buys is harmless: every
 * message carries a whole column and is idempotent, so a newcomer is served by `onChunkSent` and a departed
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
 * ### What is due when
 *
 * A fire and its scar follow something a client cannot simulate, so a change to either goes out on the next
 * flush. Footprints, wear and blood are laid by the client itself (see `GroundMarkRulesSMSG`), so a change only
 * queues a resync, due [GroundSyncConfig.resyncIntervalSeconds] after the first change the client was not told
 * about; changes inside that window share the one send. A newcomer's snapshot is always due at once, and takes
 * a resync already waiting for that column out with it.
 *
 * ### Silence means clean, and that is a deliberate difference from the static-entity batch
 *
 * `ChunkStaticEntitiesSMSG` sends an empty batch for a column with nothing on it, because a client has to know
 * when to stop waiting before it draws. This does **not**, and the asymmetry is the point: almost every column
 * in the world has never burnt and never will, so sending one anyway meant an extra message per chunk per
 * login - a hundred and twenty-odd for a view volume, for ever, to say "nothing happened here". There is also
 * nothing to wait for: un-scorched is what the terrain already draws.
 *
 * An *empty* entry is still sent when a column that was changed no longer has anything in a layer, because
 * that is the only way a healed scar retires. So the rule is: a column that **changed** is always announced, and
 * a column merely served is announced only if there is something to say.
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
  layerSources: List<GroundLayerSource>,
  /**
   * Every store of marks that have a shape and a heading. Separate from [layerSources] because they travel in
   * a different message - see `ChunkGroundStampsSMSG`.
   */
  private val stampSources: List<GroundStampSource>,
  private val sync: GroundSyncConfig,
  private val clock: BestiaClock,
) {

  /** Column -> the accounts holding its terrain. This service's own record; see the class note. */
  private val holders = ConcurrentHashMap<Long, MutableSet<Long>>()

  private val sourceOf: Map<GroundLayer, GroundLayerSource> = layerSources.associateBy { it.layer }

  /** The accounts owed a message about what is alight. Always due at once. */
  private val owedBurning = OwedColumns()

  /**
   * Per layer rather than per column, because the layers fall due at different times: a scar goes out the
   * moment it changes while the wear beside it waits for its resync, and one queue per column could not send
   * the first without dragging the second along.
   */
  private val owedLayers: Map<GroundLayer, OwedColumns> = layerSources.associate { it.layer to OwedColumns() }

  private val owedStamps = OwedColumns()

  private val queues: List<OwedColumns> = listOf(owedBurning, owedStamps) + owedLayers.values

  init {
    subscriptions.onChunkSent { accountId, chunk ->
      val column = ColumnKey.of(chunk.x, chunk.y)
      holders.computeIfAbsent(column) { ConcurrentHashMap.newKeySet() }.add(accountId)

      // Told about the ground only if there is something to say about it - see the class note.
      val newcomer = listOf(accountId)

      if (isBurning(chunk.x, chunk.y)) {
        owedBurning.owe(column, newcomer, DUE_NOW)
      }
      for ((layer, owed) in owedLayers) {
        if (!sourceOf.getValue(layer).entryAt(column).isEmpty) owed.owe(column, newcomer, DUE_NOW)
      }
      if (hasStamps(column)) {
        owedStamps.owe(column, newcomer, DUE_NOW)
      }
    }

    // Safe here: a callback runs on the tick thread inside `ChunkStreamSystem`, which is the only writer.
    subscriptions.onLastSubscriber { chunk ->
      val column = ColumnKey.of(chunk.x, chunk.y)
      val remaining = subscriptions.subscribersOfColumn(chunk.x, chunk.y)

      if (remaining.isEmpty()) {
        // Nothing is owed to nobody, and leaving the entries would grow every queue for the whole life of a
        // fire burning in a region every player has walked out of.
        holders.remove(column)
        queues.forEach { it.forget(column) }
      } else {
        holders[column]?.retainAll(remaining)
        queues.forEach { it.retainOnly(column, remaining) }
      }
    }
  }

  /** Columns with something waiting to be sent, whether or not it has fallen due yet. */
  val pending: Int
    get() {
      return queues.sumOf { it.size }
    }

  /**
   * Marks what is alight in a column stale for everyone currently holding it.
   *
   * Reads only [holders], so it is safe from any wave. Cheap and idempotent, so a fire may call it once per
   * changed cell.
   */
  fun markDirty(columnKey: Long) {
    val watchers = watchersOf(columnKey) ?: return

    owedBurning.owe(columnKey, watchers, DUE_NOW)
  }

  /**
   * Marks one layer of a column stale for everyone currently holding it, to be sent on the next flush.
   *
   * For a layer the client cannot work out for itself - a scar - and for a column whose stored marks have just
   * arrived. [markLevelsDirty] is the one for layers the client lays down itself. Safe from any wave, like
   * [markDirty].
   */
  fun markLayerDirty(columnKey: Long, layer: GroundLayer) {
    val owed = owedLayers[layer] ?: return
    val watchers = watchersOf(columnKey) ?: return

    owed.owe(columnKey, watchers, DUE_NOW)
  }

  /**
   * Marks a layer the client lays down itself - wear, blood - stale, to be resynced
   * [GroundSyncConfig.resyncIntervalSeconds] after the first change nobody was told about.
   *
   * A no-op when resyncing is switched off: the client is then left with what it worked out and what it was
   * sent on arrival.
   */
  fun markLevelsDirty(columnKey: Long, layer: GroundLayer, nowSecond: Long) {
    if (!sync.resyncs) return

    val owed = owedLayers[layer] ?: return
    val watchers = watchersOf(columnKey) ?: return

    owed.owe(columnKey, watchers, nowSecond + sync.resyncIntervalSeconds)
  }

  /**
   * Marks what has passed over a column stale, to be resynced like [markLevelsDirty].
   *
   * Called for every footfall, so it is a map insert and nothing more.
   */
  fun markStampsDirty(columnKey: Long, nowSecond: Long) {
    if (!sync.resyncs) return

    val watchers = watchersOf(columnKey) ?: return

    owedStamps.owe(columnKey, watchers, nowSecond + sync.resyncIntervalSeconds)
  }

  /**
   * Sends what has fallen due. Touches no shared state but the fan-out itself, and costs a clock read and a
   * comparison per queue on a tick where nothing has.
   *
   * @return how many messages were encoded, which is one per column and recipient group however many were in it
   */
  fun flush(): Int {
    val nowSecond = clock.now().absoluteSecond

    var sent = 0
    sent += owedBurning.drainDue(nowSecond) { column, accounts -> fanOut.fanOut(accounts, burningMessageFor(column)) }
    sent += flushLayers(nowSecond)
    sent += owedStamps.drainDue(nowSecond) { column, accounts -> fanOut.fanOut(accounts, stampsMessageFor(column)) }

    return sent
  }

  /**
   * Layers falling due for one column go in one message when the same accounts are owed them, so a newcomer's
   * scar, wear and blood arrive together.
   */
  private fun flushLayers(nowSecond: Long): Int {
    if (owedLayers.values.none { it.isDue(nowSecond) }) return 0

    val due = HashMap<Long, MutableMap<Set<Long>, MutableList<GroundLayerEntry>>>()

    for ((layer, owed) in owedLayers) {
      val source = sourceOf.getValue(layer)

      owed.drainDue(nowSecond) { column, accounts ->
        due.getOrPut(column) { HashMap() }
          .getOrPut(HashSet(accounts)) { ArrayList() }
          .add(source.entryAt(column))
      }
    }

    var sent = 0

    for ((column, byAccounts) in due) {
      for ((accounts, entries) in byAccounts) {
        fanOut.fanOut(accounts, ChunkGroundLayersSMSG(chunkOf(column), entries))
        sent++
      }
    }

    return sent
  }

  private fun watchersOf(columnKey: Long): Set<Long>? {
    val watchers = holders[columnKey] ?: return null

    return if (watchers.isEmpty()) null else watchers
  }

  private fun isBurning(chunkX: Int, chunkY: Int): Boolean {
    return fire.burningIn(chunkX, chunkY)?.isEmpty == false
  }

  private fun hasStamps(column: Long): Boolean {
    return stampSources.any { it.stampsAt(column) != null }
  }

  private fun chunkOf(column: Long): ChunkPos {
    return ChunkPos(ColumnKey.chunkXOf(column), ColumnKey.chunkYOf(column), 0)
  }

  /**
   * What is alight in one column, never a diff - see the proto.
   *
   * The mask may come back null, and that message is not wasted: it is how a fire that has gone out is
   * retired, since the client replaces a column's overlay outright and an empty one means "nothing here now".
   */
  private fun burningMessageFor(column: Long): SMSG {
    val chunk = chunkOf(column)

    return ChunkGroundOverlaySMSG(
      chunk = chunk,
      burning = fire.burningIn(chunk.x, chunk.y)?.takeIf { !it.isEmpty }?.toBytes()
    )
  }

  /**
   * The whole of one column's prints, never a diff.
   *
   * May come back empty, and that message is not wasted: when every print has expired since the change that
   * queued this, an empty message is what tells a client its own prints there are gone.
   */
  private fun stampsMessageFor(column: Long): SMSG {
    return ChunkGroundStampsSMSG(
      chunk = chunkOf(column),
      stamps = stampSources.firstNotNullOfOrNull { it.stampsAt(column) }
    )
  }

  private companion object {

    /** Lower than any second there is, so an entry owed at once is due at the next flush and wins every `minOf`. */
    const val DUE_NOW = Long.MIN_VALUE
  }
}
