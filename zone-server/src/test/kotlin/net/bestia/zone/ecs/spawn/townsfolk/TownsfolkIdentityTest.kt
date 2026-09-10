package net.bestia.zone.ecs.spawn.townsfolk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * That the three indices survive being packed into one number.
 *
 * Worth its own file because everything remembered about a person will key on this. A packing that lost a
 * bit somewhere would not fail loudly; it would quietly make two villagers the same villager.
 */
class TownsfolkIdentityTest {

  @Test
  fun `the three parts come back out`() {
    val id = TownsfolkIdentity.of(settlement = 41, household = 900_000, member = 5)

    assertEquals(41, TownsfolkIdentity.settlementOf(id))
    assertEquals(900_000, TownsfolkIdentity.householdOf(id))
    assertEquals(5, TownsfolkIdentity.memberOf(id))
  }

  @Test
  fun `the extremes of every field survive the round trip`() {
    val id = TownsfolkIdentity.of(settlement = 1_048_575, household = 16_777_215, member = 255)

    assertEquals(1_048_575, TownsfolkIdentity.settlementOf(id))
    assertEquals(16_777_215, TownsfolkIdentity.householdOf(id))
    assertEquals(255, TownsfolkIdentity.memberOf(id))
  }

  @Test
  fun `neighbouring people are never the same person`() {
    // A field that overflowed into the next one shows up here and almost nowhere else: the packing would
    // still round-trip its own values while two different people collided on one number.
    val seen = HashSet<Long>()

    for (settlement in 0..3) {
      for (household in 0..40) {
        for (member in 0..7) {
          assertEquals(
            true,
            seen.add(TownsfolkIdentity.of(settlement, household, member)),
            "s$settlement/h$household/m$member collides with somebody already packed"
          )
        }
      }
    }
  }

  @Test
  fun `the settlement is the high end, so people of one town sort together`() {
    assertNotEquals(
      TownsfolkIdentity.settlementOf(TownsfolkIdentity.of(1, 0, 0)),
      TownsfolkIdentity.settlementOf(TownsfolkIdentity.of(2, 0, 0))
    )
    assertEquals(true, TownsfolkIdentity.of(1, 999, 9) < TownsfolkIdentity.of(2, 0, 0))
  }

  @Test
  fun `a field that does not fit is refused rather than truncated`() {
    assertFailsWith<IllegalArgumentException> { TownsfolkIdentity.of(1 shl 20, 0, 0) }
    assertFailsWith<IllegalArgumentException> { TownsfolkIdentity.of(0, 1 shl 24, 0) }
    assertFailsWith<IllegalArgumentException> { TownsfolkIdentity.of(0, 0, 256) }
    assertFailsWith<IllegalArgumentException> { TownsfolkIdentity.of(-1, 0, 0) }
  }
}
