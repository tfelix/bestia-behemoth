package net.bestia.zone.economy

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sizing a world by the population it is for, rather than by the size the last one happened to be.
 *
 * The numbers here are an estimate - placer gold is a share rather than a traced river - so what is
 * asserted is the shape and the order of magnitude, plus enough pinned values that moving a constant
 * shows up in a diff instead of quietly re-sizing the next world.
 */
class WorldSizingTest {

  private val sizing = WorldSizing(EconomyConfig())

  @Test
  fun `Genesis carries the population it was sized for`() {
    val carried = sizing.playersSupportedByEdgeKm(GENESIS_KM)

    assertTrue(
      abs(carried - TARGET_PLAYERS) < TARGET_PLAYERS * 0.1,
      "a 128 km world came out at $carried players against a target of $TARGET_PLAYERS"
    )
  }

  @Test
  fun `the deposit floor makes every small world hold the same gold`() {
    // Three guaranteed deposits that may not be drawn empty outweigh the abundance well past 128 km, so
    // below that threshold gold is not what decides how big a world should be. A closed-form inverse
    // would answer confidently here and be wrong.
    assertEquals(
      sizing.playersSupportedByEdgeKm(64.0),
      sizing.playersSupportedByEdgeKm(GENESIS_KM),
      "the guaranteed deposits stopped dominating somewhere below 128 km"
    )
  }

  @Test
  fun `a bigger world carries more, once the abundance overtakes the floor`() {
    val genesis = sizing.playersSupportedByEdgeKm(GENESIS_KM)
    val target = sizing.playersSupportedByEdgeKm(512.0)
    val ceiling = sizing.playersSupportedByEdgeKm(1024.0)

    assertTrue(target > genesis * 4, "512 km carried $target against 128 km's $genesis")
    assertTrue(ceiling > target * 3, "1024 km carried $ceiling against 512 km's $target")
  }

  @Test
  fun `a suggested world actually carries what it was asked to`() {
    for (players in listOf(300, 1_000, 2_000, 5_000, 10_000)) {
      val edge = sizing.suggestedEdgeKm(players)

      assertTrue(
        sizing.playersSupportedByEdgeKm(edge.toDouble()) >= players,
        "$edge km was suggested for $players players and does not carry them"
      )
      assertTrue(
        edge == 1 || sizing.playersSupportedByEdgeKm((edge - 1).toDouble()) < players,
        "$edge km is not the smallest world that carries $players players"
      )
    }
  }

  @Test
  fun `asking for more players never suggests a smaller world`() {
    var previous = 0

    for (players in 100..20_000 step 700) {
      val edge = sizing.suggestedEdgeKm(players)
      assertTrue(edge >= previous, "$players players suggested $edge km, down from $previous km")
      previous = edge
    }
  }

  @Test
  fun `the search is bounded at both ends`() {
    assertEquals(1, sizing.suggestedEdgeKm(0), "a world for nobody should not be searched for")
    assertTrue(sizing.suggestedEdgeKm(Int.MAX_VALUE) <= 8_192, "the search ran past the largest world")
  }

  @Test
  fun `a coin supply is the players it is for times their share`() {
    assertEquals(300_000_000.0, sizing.coinSupplyFor(TARGET_PLAYERS))
  }

  private companion object {
    const val GENESIS_KM = 128.0

    /** What `application.yml` sizes the shipped world for. */
    const val TARGET_PLAYERS = 300
  }
}
