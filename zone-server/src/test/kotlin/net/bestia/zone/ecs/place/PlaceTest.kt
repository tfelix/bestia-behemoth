package net.bestia.zone.ecs.place

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Place]'s whole contract is the honesty of its dirty flag.
 *
 * There is no publisher behind this component to catch a mistake: the flag is the only thing stopping an
 * unchanged answer from being re-sent to every player on every tick they move, and a flag that never sets
 * means a player who crossed a border is never told.
 */
class PlaceTest {

  @Test
  fun `assigning an equal place is not a change`() {
    val place = Place(PlaceRef("Elm Vale"))
    place.clearDirty()

    place.place = PlaceRef("Elm Vale")

    assertFalse(place.isDirty(), "a step taken inside one region must not put a message on the wire")
  }

  @Test
  fun `a different place marks the component dirty`() {
    val place = Place(PlaceRef("Elm Vale"))
    place.clearDirty()

    place.place = PlaceRef("Iron Fells")

    assertTrue(place.isDirty())
    assertEquals("Iron Fells", place.place.name)
  }

  @Test
  fun `a place survives a round trip through the envelope`() {
    val sent = PlaceComponentSMSG(entityId = 42, name = "Elm Vale")

    val decoded = sent.toBnetEnvelope().compPlace

    assertEquals(42L, decoded.entityId)
    assertEquals("Elm Vale", decoded.name)
  }

  @Test
  fun `an area name survives a round trip through the envelope`() {
    val sent = AreaNameComponentSMSG(entityId = 7, name = "Ashford", radius = 610)

    val decoded = sent.toBnetEnvelope().compAreaName

    assertEquals(7L, decoded.entityId)
    assertEquals("Ashford", decoded.name)
    assertEquals(610L, decoded.radius)
  }
}
