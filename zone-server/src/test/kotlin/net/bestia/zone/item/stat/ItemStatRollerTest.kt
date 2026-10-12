package net.bestia.zone.item.stat

import net.bestia.zone.battle.FixedRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [FixedRandom] near 1 wins every chance a template allows at 100% and rolls top quality. */
class ItemStatRollerTest {

  private val best = ItemStatRoller(FixedRandom(0.999f))
  private val worst = ItemStatRoller(FixedRandom(0f))

  @Test
  fun `an item without a template rolls nothing and keeps its own level`() {
    val sheet = ItemStatSheet(baseLevel = 20, stats = mapOf(ItemStat.STR to 4, ItemStat.DEX to 1))

    assertEquals(ItemRoll(emptyList(), 0, 25), best.roll(sheet, cap = 100))
  }

  @Test
  fun `a top roll reaches the cap and no further`() {
    val roll = best.roll(sheet(slot(ItemStat.STR)), cap = 60)

    // 60 levels are 20 points, and a top quality of 0.999 floors to 19 STR.
    assertEquals(listOf(StatValue(ItemStat.STR, 19)), roll.stats)
    assertEquals(57, roll.itemLevel)
  }

  @Test
  fun `the lowest roll is the slot's minimum`() {
    val roll = worst.roll(sheet(slot(ItemStat.STR, min = 2)), cap = 60)

    assertEquals(listOf(StatValue(ItemStat.STR, 2)), roll.stats)
  }

  @Test
  fun `stats that together pass the cap are scaled down`() {
    val roll = best.roll(sheet(slot(ItemStat.STR), slot(ItemStat.VIT)), cap = 60)

    assertEquals(listOf(StatValue(ItemStat.STR, 10), StatValue(ItemStat.VIT, 10)), roll.stats)
  }

  @Test
  fun `a slot's max holds however good the roll`() {
    val roll = best.roll(sheet(slot(ItemStat.INT, max = 3)), cap = 100)

    assertEquals(listOf(StatValue(ItemStat.INT, 3)), roll.stats)
  }

  @Test
  fun `a socket opens only while the cap has room for it`() {
    val twoSockets = ItemStatSheet(randomStats = RandomStatTemplate(socketChances = listOf(100, 100)))

    assertEquals(2, worst.roll(twoSockets, cap = 30).sockets)
    assertEquals(1, worst.roll(twoSockets, cap = 20).sockets)
    assertEquals(0, worst.roll(twoSockets, cap = 9).sockets)
  }

  private fun sheet(vararg slots: RandomStatTemplate.Slot): ItemStatSheet {
    return ItemStatSheet(randomStats = RandomStatTemplate(slots.toList()))
  }

  private fun slot(stat: ItemStat, min: Int = 1, max: Int? = null): RandomStatTemplate.Slot {
    return RandomStatTemplate.Slot(listOf(stat), min = min, max = max)
  }
}
