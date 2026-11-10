package net.bestia.zone.crafting.repair

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RepairCostTest {

  private val log = 1L
  private val stone = 2L
  private val cost = RepairCost(maxHp = 800, buildSeconds = 1800f, materials = mapOf(log to 40, stone to 30))

  @Test
  fun `a full repair takes half the kit and half the build time`() {
    assertEquals(mapOf(log to 20, stone to 15), cost.materialsFor(800))
    assertEquals(800 / 900f, cost.hpPerSecond)
  }

  @Test
  fun `a small repair rounds each material up`() {
    assertEquals(mapOf(log to 1, stone to 1), cost.materialsFor(10))
  }

  @Test
  fun `the scarcest material limits what is paid in`() {
    val deposit = cost.deposit(800, mapOf(log to 40, stone to 3))

    assertEquals(160, deposit.hp)
    assertEquals(mapOf(log to 4, stone to 3), deposit.taken)
  }

  @Test
  fun `nothing is taken when a material is missing`() {
    assertEquals(RepairCost.Deposit(0, emptyMap()), cost.deposit(800, mapOf(log to 40)))
  }

  @Test
  fun `a structure no kit makes needs only work`() {
    val work = RepairCost(maxHp = 300, buildSeconds = 60f, materials = emptyMap())

    assertEquals(RepairCost.Deposit(120, emptyMap()), work.deposit(120, emptyMap()))
  }
}
