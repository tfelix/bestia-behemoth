package net.bestia.zone.casting.sense

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.casting.sense.BrewingEvents.Brewing
import net.bestia.zone.casting.sense.BrewingEvents.Stage
import net.bestia.zone.casting.sense.EventObservation.Precision
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** An outpost at the origin, a rift knot 1.7 km to the east and an apex 4 km to the north. */
class EventObservationTest {

  private val world = testWorld()
  private val outpost = StructureEntry(1, StaticEntityKind.OBSERVATION_OUTPOST, 1, 7, Vec3L(0, 0, 0), 0f)
  private val structures = mockk<PlayerStructureRegistry> {
    every { `in`(any(), any()) } returns emptyList()
    every { `in`(0, 0) } returns listOf(outpost)
  }
  private val worldService = mockk<WorldService> { every { config } returns WorldConfig(seed = 1, widthCells = 128, heightCells = 128) }
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val knot = Brewing(1_700.0, 0.0, "mana", "rift", feltFrom = 3.0, stage = Stage.GATHERING, changesInSeconds = 5 * 3600)
  private val apex = Brewing(0.0, 4_000.0, "beasts", "apex-roamer", feltFrom = 1.0, stage = Stage.BREAKING)

  private val observedMarks = mockk<ObservedMarks>(relaxed = true)

  private val sut = EventObservation(structures, worldService, messages, { listOf(knot, apex) }, observedMarks)

  private val scholar = world.createEntity { id ->
    add(id, Account(accountId = 7))
    add(id, Master(masterId = 3))
    add(id, Position.fromVec3(Vec3L(2, 1, 0)))
  }

  @Test
  fun `level 1 feels the big knot from three kilometres but only its direction`() {
    val marks = observe(level = 1)

    assertEquals(1, marks.size)
    assertEquals(Precision.DIRECTION.ordinal.toDouble(), marks.single().sizeMetres)
    assertEquals("", marks.single().label)
    assertTrue(marks.single().eastMetres > 0.0)
  }

  @Test
  fun `level 3 tells the distance within 500 m and the kind`() {
    val knotMark = observe(level = 3).single()

    assertEquals(Precision.DISTANCE.ordinal.toDouble(), knotMark.sizeMetres)
    assertEquals(1_500.0, knotMark.eastMetres, 1e-6)
    assertEquals("mana", knotMark.label)
    assertEquals(0L, knotMark.refId, "the stage comes at level 4")
  }

  @Test
  fun `level 5 names both events, how far along they are and when the knot opens`() {
    val marks = observe(level = 5).associateBy { it.label }

    assertEquals(setOf("rift", "apex-roamer"), marks.keys)
    assertEquals(Stage.GATHERING.ordinal + 1L, marks.getValue("rift").refId)
    assertEquals(5.0, marks.getValue("rift").depthMetres)
  }

  @Test
  fun `before level 3 nothing goes on the map, since a pin would tell the distance`() {
    observe(level = 2)

    verify(exactly = 0) { observedMarks.record(any(), any(), any()) }
  }

  @Test
  fun `level 3 pins the kind on the map`() {
    val pin = mapped(level = 3).single()

    assertEquals("Something of the mana", pin.name)
    assertEquals(1_500.0, pin.east, 1e-6)
    assertNull(pin.note)
  }

  @Test
  fun `level 5 pins the event by name with its stage and time`() {
    val pins = mapped(level = 5).associateBy { it.name }

    assertEquals(setOf("Rift", "Apex roamer"), pins.keys)
    assertEquals("gathering, within 5 hours", pins.getValue("Rift").note)
    assertEquals("breaking", pins.getValue("Apex roamer").note)
  }

  @Test
  fun `away from an outpost there is nothing to cast at`() {
    world.get(scholar, Position::class)!!.apply { x = 500 }

    assertNull(sut.outpostNear(world, scholar))
  }

  private fun mapped(level: Int): List<ObservedMarks.Observed> {
    observe(level)
    val pins = slot<List<ObservedMarks.Observed>>()
    verify { observedMarks.record(7, 3, capture(pins)) }
    return pins.captured
  }

  private fun observe(level: Int): List<SenseMark> {
    sut.observe(world, scholar, level)
    val sent = slot<SenseMarksSMSG>()
    verify { messages.sendToPlayer(7, capture(sent)) }
    return sent.captured.marks
  }
}
