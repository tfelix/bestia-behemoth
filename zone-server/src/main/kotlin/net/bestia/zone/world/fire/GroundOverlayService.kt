package net.bestia.zone.world.fire

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.socket.ChunkFanOut
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
  private val scorch: ScorchRegistry,
  private val fanOut: ChunkFanOut,
  private val subscriptions: ChunkSubscriptionService,
  /**
   * `@Lazy`, because the fire marks columns dirty here and this asks the fire for its burning mask - a genuine
   * cycle in the object graph rather than a layering mistake. The two are one subsystem: what is alight, and
   * who has been told about it.
   */
  @Lazy private val fire: GroundFireService,
) {

  /** Column -> the accounts holding its terrain. This service's own record; see the class note. */
  private val holders = ConcurrentHashMap<Long, MutableSet<Long>>()

  /** Column -> the accounts owed a message about it. */
  private val owed = ConcurrentHashMap<Long, MutableSet<Long>>()

  init {
    subscriptions.onChunkSent { accountId, chunk ->
      val column = ScorchRegistry.columnKeyOf(chunk.x, chunk.y)
      holders.computeIfAbsent(column) { ConcurrentHashMap.newKeySet() }.add(accountId)

      // Told about the ground only if there is something to say about it - see the class note.
      if (hasAnything(chunk.x, chunk.y, column)) {
        owed.computeIfAbsent(column) { ConcurrentHashMap.newKeySet() }.add(accountId)
      }
    }

    // Safe here: a callback runs on the tick thread inside `ChunkStreamSystem`, which is the only writer.
    subscriptions.onLastSubscriber { chunk ->
      val column = ScorchRegistry.columnKeyOf(chunk.x, chunk.y)
      val remaining = subscriptions.subscribersOfColumn(chunk.x, chunk.y)

      if (remaining.isEmpty()) {
        // Nothing is owed to nobody, and leaving the entries would grow both maps for the whole life of a fire
        // burning in a region every player has walked out of.
        holders.remove(column)
        owed.remove(column)
      } else {
        holders[column]?.retainAll(remaining)
        owed[column]?.retainAll(remaining)
      }
    }
  }

  val pending get() = owed.size

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
   * Sends what is owed. Touches no shared state but the fan-out itself.
   *
   * @return how many messages were encoded, which is one per column however many recipients it had
   */
  fun flush(): Int {
    if (owed.isEmpty()) return 0

    var sent = 0
    for ((column, accounts) in owed) {
      if (accounts.isEmpty()) continue
      fanOut.fanOut(accounts, messageFor(column))
      sent++
    }
    owed.clear()

    return sent
  }

  /** Whether this column has anything worth a message: a scar, or something alight. */
  private fun hasAnything(chunkX: Int, chunkY: Int, column: Long): Boolean {
    if (scorch.scarOf(column)?.visible?.isEmpty == false) return true
    return fire.burningIn(chunkX, chunkY)?.isEmpty == false
  }

  /**
   * The whole truth about one column, never a diff - see the proto.
   *
   * Both masks may come back null, and that message is not wasted: it is how a healed scar is retired, since
   * the client replaces a column's overlay outright and an empty one means "clean now".
   */
  private fun messageFor(column: Long): ChunkGroundOverlaySMSG {
    val chunkX = ScorchRegistry.chunkXOf(column)
    val chunkY = ScorchRegistry.chunkYOf(column)

    return ChunkGroundOverlaySMSG(
      chunk = ChunkPos(chunkX, chunkY, 0),
      // `visible`, not `mask`: the stored mask is the original burn and a healing scar is smaller than it.
      scorched = scorch.scarOf(column)?.visible?.takeIf { !it.isEmpty }?.toBytes(),
      burning = fire.burningIn(chunkX, chunkY)?.takeIf { !it.isEmpty }?.toBytes()
    )
  }
}
