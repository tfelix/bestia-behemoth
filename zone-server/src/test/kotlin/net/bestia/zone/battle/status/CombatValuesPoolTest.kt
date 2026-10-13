package net.bestia.zone.battle.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class CombatValuesPoolTest {

  private val pool = CombatValuesPool()

  private fun valuesAt(level: Int) = CombatValues.ofBase(
    level = level,
    base = StatusValues(strength = 5, vitality = 5, intelligence = 5, agility = 5, willpower = 5, dexterity = 5)
  )

  @Test
  fun `equal values become one instance`() {
    val first = pool.share(valuesAt(10))
    val second = pool.share(valuesAt(10))

    assertSame(first, second)
    assertEquals(1, pool.size)
  }

  @Test
  fun `different values stay different`() {
    val low = pool.share(valuesAt(10))
    val high = pool.share(valuesAt(40))

    assertNotSame(low, high)
    assertEquals(2, pool.size)
  }

  @Test
  fun `past the cap a new value is handed back unpooled and what is pooled stays shared`() {
    val pooled = pool.share(valuesAt(1))
    for (level in 2..CombatValuesPool.MAX_SHARED) {
      pool.share(valuesAt(level))
    }
    assertEquals(CombatValuesPool.MAX_SHARED, pool.size)

    val overflow = valuesAt(CombatValuesPool.MAX_SHARED + 1)
    assertSame(overflow, pool.share(overflow))
    assertEquals(CombatValuesPool.MAX_SHARED, pool.size, "the pool grew past its cap")

    assertSame(pooled, pool.share(valuesAt(1)))
  }
}
