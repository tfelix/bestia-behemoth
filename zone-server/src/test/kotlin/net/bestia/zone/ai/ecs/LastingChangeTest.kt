package net.bestia.zone.ai.ecs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LastingChangeTest {

  private val changes = LastingChange<Int, String>(holdMs = 60)

  @Test
  fun `the first value is told once it has held`() {
    assertNull(changes.see(1, "WORKING", at = 0))
    assertNull(changes.see(1, "WORKING", at = 59))

    assertEquals(LastingChange.Change(null, "WORKING"), changes.see(1, "WORKING", at = 60))
    assertNull(changes.see(1, "WORKING", at = 200))
  }

  @Test
  fun `a value that flicks back before it held tells nothing`() {
    changes.see(1, "WORKING", at = 0)
    changes.see(1, "WORKING", at = 60)

    assertNull(changes.see(1, "RESTING", at = 70))
    assertNull(changes.see(1, "WORKING", at = 100))
    assertNull(changes.see(1, "WORKING", at = 300))
  }

  @Test
  fun `a lasting change is told from the value before it`() {
    changes.see(1, "WORKING", at = 0)
    changes.see(1, "WORKING", at = 60)
    changes.see(1, "RESTING", at = 100)

    assertNull(changes.see(1, "RESTING", at = 159))
    assertEquals(LastingChange.Change("WORKING", "RESTING"), changes.see(1, "RESTING", at = 160))
  }

  @Test
  fun `a key that is dropped starts over`() {
    changes.see(1, "WORKING", at = 0)
    changes.see(1, "WORKING", at = 60)

    changes.retainAll(emptySet())
    changes.see(1, "WORKING", at = 100)

    assertEquals(LastingChange.Change(null, "WORKING"), changes.see(1, "WORKING", at = 160))
  }
}
