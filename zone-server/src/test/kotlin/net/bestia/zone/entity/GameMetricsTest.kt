package net.bestia.zone.entity

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GameMetricsTest {

  private val meters = SimpleMeterRegistry()
  private val metrics = GameMetrics(meters)

  private fun count(name: String, vararg tags: String): Double {
    return meters.get(name).tags(*tags).counter().count()
  }

  @Test
  fun `hits and the damage they dealt are counted together`() {
    metrics.hits(3, 450)

    assertEquals(3.0, count("zone.combat.hits"))
    assertEquals(450.0, count("zone.combat.damage"))
  }

  @Test
  fun `deaths are counted by who died`() {
    metrics.died(player = false)
    metrics.died(player = false)
    metrics.died(player = true)

    assertEquals(2.0, count("zone.combat.deaths", "victim", "mob"))
    assertEquals(1.0, count("zone.combat.deaths", "victim", "player"))
  }

  @Test
  fun `a charge to the reserve is coin minted, and a negative one is coin returned`() {
    metrics.reserveCharged(100.0)
    metrics.reserveCharged(-30.0)

    assertEquals(100.0, count("zone.economy.coins", "flow", "minted"))
    assertEquals(30.0, count("zone.economy.coins", "flow", "returned"))
  }

  @Test
  fun `a merchant trade counts the trade, the goods and the coin under the player's direction`() {
    metrics.shopTrade(selling = true, units = 5, coins = 80)

    assertEquals(1.0, count("zone.economy.shop.trades", "direction", "sell"))
    assertEquals(5.0, count("zone.economy.shop.units", "direction", "sell"))
    assertEquals(80.0, count("zone.economy.shop.coins", "direction", "sell"))
    assertEquals(0.0, count("zone.economy.shop.trades", "direction", "buy"))
  }

  @Test
  fun `refusals and trade endings get a series the first time they happen`() {
    metrics.shopRefused("OUT_OF_STOCK")
    metrics.shopRefused("OUT_OF_STOCK")
    metrics.tradeEnded("completed")
    metrics.tradeEnded("EXPIRED")

    assertEquals(2.0, count("zone.economy.shop.refusals", "reason", "out_of_stock"))
    assertEquals(1.0, count("zone.trade.ended", "reason", "completed"))
    assertEquals(1.0, count("zone.trade.ended", "reason", "expired"))
  }

  @Test
  fun `crafts, despawns and granted items are counted`() {
    metrics.crafted(GameMetrics.Craft.SUCCESS)
    metrics.crafted(GameMetrics.Craft.FAILED)
    metrics.despawned(GameMetrics.Despawn.DEN_PACK, 12)
    metrics.despawned(GameMetrics.Despawn.AMBIENT, 0)
    metrics.itemsGranted(7)
    metrics.mobSpawned()

    assertEquals(1.0, count("zone.crafting.results", "result", "success"))
    assertEquals(0.0, count("zone.crafting.results", "result", "item_destroyed"))
    assertEquals(12.0, count("zone.spawn.despawned", "reason", "den_pack"))
    assertEquals(0.0, count("zone.spawn.despawned", "reason", "ambient"))
    assertEquals(7.0, count("zone.items.granted"))
    assertEquals(1.0, count("zone.spawn.mobs"))
  }
}
