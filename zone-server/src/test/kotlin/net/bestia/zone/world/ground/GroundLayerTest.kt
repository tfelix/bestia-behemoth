package net.bestia.zone.world.ground

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the two numbers a client decodes by.
 *
 * Neither is checkable at runtime: any byte string is a legal payload, so a disagreement about a channel
 * repaints the world in the wrong colours rather than throwing. The client holds the mirror of this test.
 */
class GroundLayerTest {

  @Test
  fun `wire ids are what the client decodes by`() {
    assertEquals(1, GroundLayer.SCORCHED.wireId)
    assertEquals(2, GroundLayer.WORN.wireId)
    assertEquals(3, GroundLayer.BLOODIED.wireId)
    assertEquals(4, GroundLayer.DISTURBED.wireId)
  }

  @Test
  fun `channels are what the client composites by`() {
    assertEquals(0, GroundLayer.SCORCHED.channel)
    assertEquals(1, GroundLayer.WORN.channel)
    assertEquals(2, GroundLayer.BLOODIED.channel)
    assertEquals(3, GroundLayer.DISTURBED.channel)
  }

  @Test
  fun `every layer has its own wire id and its own channel`() {
    assertEquals(GroundLayer.entries.size, GroundLayer.entries.map { it.wireId }.toSet().size)
    assertEquals(GroundLayer.entries.size, GroundLayer.entries.map { it.channel }.toSet().size)
  }

  @Test
  fun `layers fit the channels one mark texture has`() {
    assertTrue(GroundLayer.entries.size <= GroundLayer.CHANNELS)
    assertTrue(GroundLayer.entries.all { it.channel in 0 until GroundLayer.CHANNELS })
  }

  @Test
  fun `zero is nobody said, not the first layer`() {
    assertNull(GroundLayer.ofWireId(0))
  }

  @Test
  fun `an unknown wire id is refused rather than guessed`() {
    assertNull(GroundLayer.ofWireId(99))
  }

  @Test
  fun `every layer round trips its wire id`() {
    GroundLayer.entries.forEach { assertEquals(it, GroundLayer.ofWireId(it.wireId)) }
  }
}
