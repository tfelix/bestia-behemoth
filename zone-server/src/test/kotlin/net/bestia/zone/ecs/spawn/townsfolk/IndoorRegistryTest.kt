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
    sut.enter(villager, DOOR, night, ON_TIME)

    assertTrue(sut.isIndoors(villager))
    assertFalse(sut.isIndoors(shopkeeper))
    assertEquals(1, sut.size)
  }

  @Test
  fun `they come out when the clock leaves their window, not before`() {
    sut.enter(villager, DOOR, night, ON_TIME)

    assertEquals(emptyList(), sut.dueOut(at(23)).map { it.identity }, "it is still the middle of their night")
    assertEquals(emptyList(), sut.dueOut(at(0)).map { it.identity }, "and midnight is inside it")
    assertEquals(listOf(villager), sut.dueOut(at(6)).map { it.identity })
  }

  @Test
  fun `two people on different hours come out at different times`() {
    sut.enter(villager, DOOR, night, ON_TIME)
    sut.enter(shopkeeper, DOOR, shift, ON_TIME)

    assertEquals(listOf(shopkeeper), sut.dueOut(at(2)).map { it.identity }, "the shop is shut in the small hours")
    assertEquals(listOf(villager), sut.dueOut(at(12)).map { it.identity }, "and open at noon")
  }

  @Test
  fun `somebody whose day sits late comes out late`() {
    // The record carries the offset because the sweep has a record and nobody to ask. Without it a street
    // refills in one step, which is the thing the offset exists to stop.
    sut.enter(villager, DOOR, night, ON_TIME)
    sut.enter(shopkeeper, DOOR, night, LATE)

    assertEquals(listOf(villager), sut.dueOut(at(6)).map { it.identity }, "one is up and one is not")
    assertEquals(setOf(villager, shopkeeper), sut.dueOut(at(7)).map { it.identity }.toSet())
  }

  @Test
  fun `leaving hands back where they went in`() {
    sut.enter(villager, DOOR, night, ON_TIME)

    assertEquals(DOOR, sut.leave(villager)?.door)
    assertFalse(sut.isIndoors(villager))
    assertNull(sut.leave(villager), "leaving twice is not a second person")
  }

  @Test
  fun `a town can be forgotten whole`() {
    sut.enter(villager, DOOR, night, ON_TIME)
    sut.enter(shopkeeper, DOOR, night, ON_TIME)
    sut.enter(TownsfolkIdentity.of(settlement = 9, household = 0, member = 0), DOOR, night, ON_TIME)

    sut.forgetSettlement(3)

    assertEquals(1, sut.size, "only the other town's people should be left")
    assertFalse(sut.isIndoors(villager))
  }

  private fun at(hour: Int): Int {
    return hour * HourWindow.MINUTES_PER_HOUR
  }

  private companion object {
    val DOOR = Vec3L(120, 340, 5)

    const val ON_TIME = 0
    const val LATE = 30
  }
}
