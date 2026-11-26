package net.bestia.zone.item.ecs

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LootProtectionTest {

  private val protection = LootProtection(listOf(LootClaim(setOf(1L), 3f), LootClaim(setOf(1L, 2L), 6f)))

  @Test
  fun `clients are told again when the claim passes on`() {
    protection.clearDirty()

    protection.countDown(2f)
    assertFalse(protection.isDirty(), "the same holders, nothing to tell")

    protection.countDown(1f)
    assertTrue(protection.isDirty())
    assertEquals(LootClaimComponentSMSG(7L, setOf(1L, 2L), removed = false), protection.toEntityMessage(7L))
  }

  @Test
  fun `the last claim running out names nobody`() {
    assertFalse(protection.countDown(9f))

    assertEquals(LootClaimComponentSMSG(7L, emptySet(), removed = true), protection.toEntityMessage(7L, removed = true))
  }
}
