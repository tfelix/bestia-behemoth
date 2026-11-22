package net.bestia.zone.battle

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaturalArmorTest {

  @Test
  fun `a dragon's hide grows with it`() {
    assertEquals(NaturalArmor.Armor(3, 2), NaturalArmor.of(BestiaKind.DRAGON, 10, boss = false))
    assertEquals(NaturalArmor.Armor(30, 20), NaturalArmor.of(BestiaKind.DRAGON, 100, boss = false))
  }

  @Test
  fun `a boss is half again as hard, and nothing passes the cap`() {
    assertEquals(NaturalArmor.Armor(45, 30), NaturalArmor.of(BestiaKind.DRAGON, 100, boss = true))
    assertTrue(NaturalArmor.of(BestiaKind.ELEMENTAL, 500, boss = true).magicDefense <= NaturalArmor.CAP)
  }

  @Test
  fun `a body of spirit resists spells more than blades`() {
    val elemental = NaturalArmor.of(BestiaKind.ELEMENTAL, 50, boss = false)

    assertTrue(elemental.magicDefense > elemental.defense)
  }

  @Test
  fun `no kind, no natural armour`() {
    assertEquals(NaturalArmor.Armor.NONE, NaturalArmor.of(null, 99, boss = true))
  }

  @Test
  fun `every kind has a row`() {
    for (kind in BestiaKind.entries) assertTrue(NaturalArmor.of(kind, 100, boss = false) != NaturalArmor.Armor.NONE, "$kind")
  }
}
