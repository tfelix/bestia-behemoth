package net.bestia.zone.item.stat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The worked examples of the docs' forging page. */
class ItemLevelTest {

  @Test
  fun `a plain shirt with five intelligence is level 15`() {
    assertEquals(15, ItemLevel.of(baseLevel = 1, stats = listOf(StatValue(ItemStat.INT, 5))))
  }

  @Test
  fun `the iron sword's fixed stats lift it from 20 to 25`() {
    val stats = listOf(StatValue(ItemStat.STR, 4), StatValue(ItemStat.DEX, 1))

    assertEquals(25, ItemLevel.of(baseLevel = 20, stats = stats))
  }

  @Test
  fun `a strong base absorbs part of a big bonus`() {
    assertEquals(112, ItemLevel.of(baseLevel = 95, stats = listOf(StatValue(ItemStat.STR, 20))))
  }

  @Test
  fun `derived stats cost what one attribute point yields`() {
    val onePointEach = listOf(
      StatValue(ItemStat.ATK, 2),
      StatValue(ItemStat.CRIT, 1),
      StatValue(ItemStat.HP_REGEN, 5),
      StatValue(ItemStat.RESIST_FIRE, 3)
    )

    // 2 ATK, 5% regen and 3% resistance are a point each, and 1 CRIT is three.
    assertEquals(6.0, ItemLevel.pointsOf(onePointEach), 1e-9)
  }

  @Test
  fun `a socket counts five points`() {
    assertEquals(30, ItemLevel.of(baseLevel = 1, stats = emptyList(), sockets = 2))
  }

  @Test
  fun `a drawback lowers the level but never below the base`() {
    val heavy = listOf(StatValue(ItemStat.VIT, 5), StatValue(ItemStat.AGI, -2))
    val cursed = listOf(StatValue(ItemStat.AGI, -5))

    assertEquals(9, ItemLevel.of(baseLevel = 1, stats = heavy))
    assertEquals(10, ItemLevel.of(baseLevel = 10, stats = cursed))
  }
}
