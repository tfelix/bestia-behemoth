package net.bestia.zone.battle.potency

import net.bestia.zone.battle.status.StatusResistance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The worked examples from the docs' "Spell Potency and Rituals" page. */
class PotencyTest {

  @Test
  fun `the example casters reach the potency the docs give`() {
    // Rank and Level as the docs' table states them; Mind from INT + WIL / 2.
    assertEquals(18, Potency.rankTerm(3, 5))
    assertEquals(21, Potency.rankTerm(Potency.BESTIA_RANK_PER_LEVEL * 7, Potency.MAX_RANK))
    assertEquals(15, Potency.mind(intelligence = 100, willpower = 0))
    assertEquals(80, 30 + 25 + 20 + 5 + 0)
    assertEquals(80, Potency.of(rank = 5, maxRank = 5, level = 100, intelligence = 200, willpower = 0, focus = 5, place = 0))
  }

  @Test
  fun `edge is a coin flip when equal and certain at a lead of 25`() {
    assertEquals(0.5, Potency.edge(40, 40))
    assertEquals(0.7, Potency.edge(50, 40), 1e-9)
    assertEquals(1.0, Potency.edge(65, 40))
    assertEquals(0.0, Potency.edge(15, 40))
  }

  @Test
  fun `dispel keeps its base chance unless the target is stronger`() {
    assertEquals(100.0, Potency.dispelChancePercent(100, dispel = 80, target = 49))
    assertEquals(50.0, Potency.dispelChancePercent(100, dispel = 80, target = 160))
    assertEquals(0.0, Potency.dispelChancePercent(100, dispel = 110, target = 3000, threshold = 120))
  }

  @Test
  fun `a master caster cuts through a brute's resistance`() {
    // VIT 50 + STR 30 against a level-40 bestia with Resolve 46: 48% against the dabbler, 1% against the master.
    assertEquals(0.48, StatusResistance.ofPotency(statSum = 80, resolve = 46, potency = 33), 0.01)
    assertEquals(0.01, StatusResistance.ofPotency(statSum = 80, resolve = 46, potency = 80), 0.01)
  }

  @Test
  fun `the surge is small and capped`() {
    assertEquals(20, Potency.surgePercent(80))
    assertEquals(25, Potency.surgePercent(400))
  }
}
