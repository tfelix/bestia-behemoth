package net.bestia.zone.entity

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * What happens in the game itself, counted where it happens: damage and deaths, creatures appearing and going,
 * coin entering and leaving the world, trades, crafts and items obtained. The economy ones are the early warning
 * for a duplication bug, because coin and items are only ever created at a few places and these say how much.
 *
 * Lives in the `entity` slice, which every feature that reports here may depend on; the metrics package sits
 * above them all and so cannot be the one they call. Recorded on the tick thread and on DB workers, which
 * Micrometer's counters allow. The default registry serves tests that build the systems by hand.
 */
@Component
class GameMetrics(private val registry: MeterRegistry = SimpleMeterRegistry()) {

  /** Why a creature left the world without dying. */
  enum class Despawn {
    /** Its den went dormant because no player is near. */
    DEN_PACK,

    /** Nobody was near its ambient site. */
    AMBIENT,

    /** Nobody was near the town it lives in. */
    TOWNSFOLK,
  }

  /** How a craft ended once it was resolved. */
  enum class Craft {
    SUCCESS, FAILED, ITEM_DESTROYED
  }

  /** Who moved coin out of the world reserve or back into it. */
  enum class CoinSource {
    /** A settlement's treasury drifting toward its reference, and its trade beyond the map. */
    SETTLEMENT,

    /** A player striking coin from a gold bar. */
    MINT,

    /** An auction listing fee. */
    AUCTION_FEE,
  }

  private val hits = Counter.builder("zone.combat.hits")
    .description("Hits applied to a creature or player, after immunity")
    .register(registry)

  private val damage = Counter.builder("zone.combat.damage")
    .description("Damage points those hits dealt")
    .register(registry)

  private val deaths = listOf("mob", "player").associateWith { victim ->
    Counter.builder("zone.combat.deaths")
      .tag("victim", victim)
      .description("Creatures and players that died; a mob is counted when it is removed and pays out")
      .register(registry)
  }

  private val mobsSpawned = Counter.builder("zone.spawn.mobs")
    .description("Creatures created, wild, townsfolk, owned and restored from the database alike")
    .register(registry)

  private val despawns = Despawn.entries.associateWith { reason ->
    Counter.builder("zone.spawn.despawned")
      .tag("reason", reason.name.lowercase())
      .description("Creatures removed because no player was near, not because they died")
      .register(registry)
  }

  private val coins = listOf("minted", "returned").associateWith { flow ->
    CoinSource.entries.associateWith { source ->
      Counter.builder("zone.economy.coins")
        .tag("flow", flow)
        .tag("source", source.name.lowercase())
        .description("Coin leaving the world reserve to someone (minted) and going back to it (returned); the total is constant otherwise")
        .register(registry)
    }
  }

  private val shopTrades = listOf("buy", "sell").associateWith { direction ->
    Counter.builder("zone.economy.shop.trades")
      .tag("direction", direction)
      .description("Completed trades with a merchant; direction is the player's")
      .register(registry)
  }

  private val shopUnits = listOf("buy", "sell").associateWith { direction ->
    Counter.builder("zone.economy.shop.units")
      .tag("direction", direction)
      .description("Goods moved in those trades")
      .register(registry)
  }

  private val shopCoins = listOf("buy", "sell").associateWith { direction ->
    Counter.builder("zone.economy.shop.coins")
      .tag("direction", direction)
      .description("Coin moved in those trades")
      .register(registry)
  }

  private val crafts = Craft.entries.associateWith { result ->
    Counter.builder("zone.crafting.results")
      .tag("result", result.name.lowercase())
      .description("Crafts by how they ended")
      .register(registry)
  }

  private val itemsGranted = Counter.builder("zone.items.granted")
    .description("Items put into a live inventory: pickups, crafts, shop and trade deliveries, rewards")
    .register(registry)

  private val shopRefusals = ConcurrentHashMap<String, Counter>()
  private val tradesEnded = ConcurrentHashMap<String, Counter>()

  fun hits(count: Int, totalDamage: Long) {
    hits.increment(count.toDouble())
    damage.increment(totalDamage.toDouble())
  }

  fun died(player: Boolean) {
    deaths.getValue(if (player) "player" else "mob").increment()
  }

  fun mobSpawned() {
    mobsSpawned.increment()
  }

  fun despawned(reason: Despawn, count: Int = 1) {
    if (count > 0) despawns.getValue(reason).increment(count.toDouble())
  }

  /** The reserve was charged [amount]: positive hands coin out into the world, negative takes it back. */
  fun reserveCharged(amount: Double, source: CoinSource) {
    val flow = if (amount > 0) "minted" else "returned"
    coins.getValue(flow).getValue(source).increment(abs(amount))
  }

  fun shopTrade(selling: Boolean, units: Int, coins: Long) {
    val direction = if (selling) "sell" else "buy"
    shopTrades.getValue(direction).increment()
    shopUnits.getValue(direction).increment(units.toDouble())
    shopCoins.getValue(direction).increment(coins.toDouble())
  }

  /** A merchant said no. [reason] is one of the shop's own refusals, a short fixed list. */
  fun shopRefused(reason: String) {
    shopRefusals.computeIfAbsent(reason) { name ->
      Counter.builder("zone.economy.shop.refusals")
        .tag("reason", name.lowercase())
        .description("Merchant trades that were refused, by why")
        .register(registry)
    }.increment()
  }

  fun crafted(result: Craft) {
    crafts.getValue(result).increment()
  }

  /** A player-to-player trade ended; [reason] is `completed` or one of the trade's own end reasons. */
  fun tradeEnded(reason: String) {
    tradesEnded.computeIfAbsent(reason) { name ->
      Counter.builder("zone.trade.ended")
        .tag("reason", name.lowercase())
        .description("Player trades by how they ended; completed ones exchanged goods")
        .register(registry)
    }.increment()
  }

  fun itemsGranted(amount: Int) {
    if (amount > 0) itemsGranted.increment(amount.toDouble())
  }
}
