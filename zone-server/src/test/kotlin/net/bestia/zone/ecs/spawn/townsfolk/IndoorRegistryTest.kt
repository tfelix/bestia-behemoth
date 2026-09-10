package net.bestia.zone.ecs.spawn.townsfolk

import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one line kept per person who has gone inside, and the question asked of it: who is due out.
 *
 * Against the clock rather than a countdown, and that is not a stylistic choice. Nothing ticks an indoor
 * record - that is the point of them - so a duration would need somebody counting it down while the town
 * is empty, which is exactly the cost the registry exists to avoid.
 */
class IndoorRegistryTest {

  private val sut = IndoorRegistry()

  private val night = HourWindow(22, 6)
  private val shift = HourWindow(9, 17)

  private val villager = TownsfolkIdentity.of(settlement = 3, household = 7, member = 1)
  private val shopkeeper = TownsfolkIdentity.of(settlement = 3, household = 8, member = 0)

  @Test
  fun `somebody who has gone in is in`() {
    sut.enter(villager, DOOR, night)

    assertTrue(sut.isIndoors(villager))
    assertFalse(sut.isIndoors(shopkeeper))
    assertEquals(1, sut.size)
  }

  @Test
  fun `they come out when the clock leaves their window, not before`() {
    sut.enter(villager, DOOR, night)

    assertEquals(emptyList(), sut.dueOut(23).map { it.identity }, "it is still the middle of their night")
    assertEquals(emptyList(), sut.dueOut(0).map { it.identity }, "and midnight is inside it")
    assertEquals(listOf(villager), sut.dueOut(6).map { it.identity })
  }

  @Test
  fun `two people on different hours come out at different times`() {
    sut.enter(villager, DOOR, night)
    sut.enter(shopkeeper, DOOR, shift)

    assertEquals(listOf(shopkeeper), sut.dueOut(2).map { it.identity }, "the shop is shut in the small hours")
    assertEquals(listOf(villager), sut.dueOut(12).map { it.identity }, "and open at noon")
  }

  @Test
  fun `leaving hands back where they went in`() {
    sut.enter(villager, DOOR, night)

    assertEquals(DOOR, sut.leave(villager)?.door)
    assertFalse(sut.isIndoors(villager))
    assertNull(sut.leave(villager), "leaving twice is not a second person")
  }

  @Test
  fun `a town can be forgotten whole`() {
    sut.enter(villager, DOOR, night)
    sut.enter(shopkeeper, DOOR, night)
    sut.enter(TownsfolkIdentity.of(settlement = 9, household = 0, member = 0), DOOR, night)

    sut.forgetSettlement(3)

    assertEquals(1, sut.size, "only the other town's people should be left")
    assertFalse(sut.isIndoors(villager))
  }

  private companion object {
    val DOOR = Vec3L(120, 340, 5)
  }
}
