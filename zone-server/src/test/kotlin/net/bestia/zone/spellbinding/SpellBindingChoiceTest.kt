package net.bestia.zone.spellbinding

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpellBindingChoiceTest {

  @Test
  fun `a ground binding reads scroll, trigger and filter`() {
    val choice = SpellBindingChoice.parse(listOf(1, 77, 1, 4, 0))

    assertEquals(SpellBindingChoice.Bind(77, BindingTrigger.STEPS_ON, BindingFilter.WILD_ONLY, 0, null), choice)
  }

  @Test
  fun `a delay is read only for the delay trigger, and only within 5 s to 5 min`() {
    val delayed = SpellBindingChoice.parse(listOf(1, 77, 3, 1, 30)) as SpellBindingChoice.Bind

    assertEquals(30, delayed.delaySeconds)
    assertNull(SpellBindingChoice.parse(listOf(1, 77, 3, 1, 4)))
    assertNull(SpellBindingChoice.parse(listOf(1, 77, 3, 1, 301)))
  }

  @Test
  fun `an item trigger reads the item's unique id`() {
    val onItem = SpellBindingChoice.parse(listOf(1, 77, 4, 1, 900)) as SpellBindingChoice.Bind

    assertEquals(900, onItem.itemUniqueId)
  }

  @Test
  fun `a release names the binding, and anything malformed is no choice`() {
    assertEquals(SpellBindingChoice.Release(12), SpellBindingChoice.parse(listOf(2, 12)))
    assertNull(SpellBindingChoice.parse(emptyList()))
    assertNull(SpellBindingChoice.parse(listOf(1, 77, 99, 1, 0)))
    assertNull(SpellBindingChoice.parse(listOf(1, 77, 1, 1)))
  }

  @Test
  fun `each level binds one more and a stronger attack, level 5 any`() {
    assertEquals(3, SpellBindingRules.maxBindings(3))
    assertEquals(60, SpellBindingRules.maxAttackLevel(3))
    assertEquals(Int.MAX_VALUE, SpellBindingRules.maxAttackLevel(5))
  }
}
