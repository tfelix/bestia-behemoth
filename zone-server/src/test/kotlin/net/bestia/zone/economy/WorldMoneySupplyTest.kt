package net.bestia.zone.economy

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A treasury is a share of a fixed supply rather than an amount per resident, and this is what that buys:
 * the treasuries sum to the coin the world says NPCs hold, on any seed. Per resident they sum to whatever
 * population the history simulation arrived at, which is a different number on every world.
 */
class WorldMoneySupplyTest {

  @Test
  fun `the settlements' shares sum to what NPCs hold`() {
    val random = Random(0xC01)
    val towns = List(280) { random.nextInt(20, 40_000) to random.nextDouble(0.0, 1.0) }

    val total = towns.sumOf { (population, wealth) -> WorldMoneySupply.weightOf(population, wealth) }
    val allocated = towns.sumOf { (population, wealth) ->
      WorldMoneySupply.shareOf(NPC_HOLDINGS, WorldMoneySupply.weightOf(population, wealth), total)
    }

    assertTrue(
      abs(allocated - NPC_HOLDINGS) < NPC_HOLDINGS * 1e-9,
      "the towns were allocated $allocated of $NPC_HOLDINGS, so the split creates or strands coin"
    )
  }

  @Test
  fun `the only settlement in a world holds all of it`() {
    val weight = WorldMoneySupply.weightOf(population = 900, wealth = 0.4)

    assertEquals(NPC_HOLDINGS, WorldMoneySupply.shareOf(NPC_HOLDINGS, weight, weight))
  }

  @Test
  fun `a world with nobody in it allocates nothing rather than dividing by it`() {
    assertEquals(0.0, WorldMoneySupply.shareOf(NPC_HOLDINGS, weight = 0.0, totalWeight = 0.0))
  }

  @Test
  fun `a city outweighs a hamlet, and wealth tilts a share without deciding it`() {
    assertTrue(
      WorldMoneySupply.weightOf(20_000, 0.2) > WorldMoneySupply.weightOf(120, 1.0),
      "the richest possible hamlet outweighed a city, so wealth is the key rather than a tilt"
    )
    assertTrue(
      WorldMoneySupply.weightOf(600, 0.9) > WorldMoneySupply.weightOf(600, 0.1),
      "wealth made no difference between two towns of the same size"
    )
  }

  @Test
  fun `the fallback keeps the same shape, so a test town is proportioned like a real one`() {
    val poor = PerResidentTreasury.DEFAULT.treasuryFor(population = 300, wealth = 0.1)
    val rich = PerResidentTreasury.DEFAULT.treasuryFor(population = 300, wealth = 0.9)

    assertTrue(rich > poor, "wealth has to move the fallback purse too, or tests prove nothing about it")
  }

  private companion object {
    /** Genesis: 300,000,000 coins, half of them in NPC hands. */
    const val NPC_HOLDINGS = 150_000_000.0
  }
}
