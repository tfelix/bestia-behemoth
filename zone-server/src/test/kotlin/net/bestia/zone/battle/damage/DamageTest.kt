package net.bestia.zone.battle.damage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The non-negative guard, which is all the behaviour [Damage] has of its own. It passed silently for years
 * while it read the property instead of the constructor parameter, so it is worth pinning.
 */
class DamageTest {

  @Test
  fun `a negative amount is refused`() {
    assertThrows<IllegalArgumentException> { HitDamage(-1) }
    assertThrows<IllegalArgumentException> { Heal(-1) }
    assertThrows<IllegalArgumentException> { CriticalHit(-1) }
    assertThrows<IllegalArgumentException> { TrueDamage(-1) }
  }

  @Test
  fun `zero is allowed, because a miss is one`() {
    assertEquals(0, Miss.amount)
    assertEquals(0, HitDamage(0).amount)
  }
}
