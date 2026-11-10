package net.bestia.zone.battle.damage

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DamageModifiersTest {

  private val sword = HitKind.physical(HitReach.MELEE)

  @Test
  fun `reductions in one category add up`() {
    val defender = side(DamageModifiers(takenFromKind = mapOf(BestiaKind.HUMANOID to 60)))

    assertEquals(40, DamageModifiers.weigh(100, sword, side(kind = BestiaKind.HUMANOID), defender))
  }

  @Test
  fun `categories multiply`() {
    val defender = side(
      DamageModifiers(
        takenFromKind = mapOf(BestiaKind.HUMANOID to 30),
        takenByReach = mapOf(HitReach.MELEE to 20),
      )
    )

    assertEquals(56, DamageModifiers.weigh(100, sword, side(kind = BestiaKind.HUMANOID), defender))
  }

  @Test
  fun `the attacker's bonus against the defender's kind raises the hit`() {
    val attacker = side(DamageModifiers(dealtVsKind = mapOf(BestiaKind.DEMON to 25)))

    assertEquals(125, DamageModifiers.weigh(100, sword, attacker, side(kind = BestiaKind.DEMON)))
    assertEquals(100, DamageModifiers.weigh(100, sword, attacker, side(kind = BestiaKind.BEAST)))
  }

  @Test
  fun `a physical reduction leaves a spell alone`() {
    val defender = side(DamageModifiers(takenByForm = mapOf(HitForm.PHYSICAL to 15)))

    assertEquals(85, DamageModifiers.weigh(100, sword, null, defender))
    assertEquals(100, DamageModifiers.weigh(100, HitKind.magic(Element.FIRE), null, defender))
  }

  @Test
  fun `a full reduction stops the hit and an untyped hit is never weighed`() {
    val defender = side(DamageModifiers(takenByForm = mapOf(HitForm.MAGIC to 120)))

    assertEquals(0, DamageModifiers.weigh(100, HitKind.magic(), null, defender))
    assertEquals(100, DamageModifiers.weigh(100, HitKind.UNTYPED, null, defender))
  }

  private fun side(
    modifiers: DamageModifiers = DamageModifiers.NONE,
    kind: BestiaKind? = null,
  ) = DamageModifiers.Side(modifiers, kind, Element.NORMAL, boss = false)
}
