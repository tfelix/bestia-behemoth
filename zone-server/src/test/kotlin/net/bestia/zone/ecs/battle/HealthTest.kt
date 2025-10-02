package net.bestia.zone.ecs.battle

import net.bestia.zone.component.HealthComponentSMSG
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HealthTest {

  @Test
  fun `constructor with values sets current and max correctly`() {
    val health = Health(50, 100)

    assertEquals(50, health.current)
    assertEquals(100, health.max)
  }

  @Test
  fun `isDirty returns true initially after construction`() {
    val health = Health(50, 100)

    assertTrue(health.isDirty())
  }

  @Test
  fun `clearDirty sets dirty flag to false`() {
    val health = Health(50, 100)

    health.clearDirty()

    assertFalse(health.isDirty())
  }

  @Test
  fun `setting current to same value does not set dirty flag`() {
    val health = Health(50, 100)
    health.clearDirty()

    health.current = 50

    assertFalse(health.isDirty())
  }

  @Test
  fun `setting current to different valid value sets dirty flag`() {
    val health = Health(50, 100)
    health.clearDirty()

    health.current = 60

    assertTrue(health.isDirty())
    assertEquals(60, health.current)
  }

  @Test
  fun `setting max to same value does not set dirty flag`() {
    val health = Health(50, 100)
    health.clearDirty()

    health.max = 100

    assertFalse(health.isDirty())
  }

  @Test
  fun `setting max to different value sets dirty flag`() {
    val health = Health(50, 100)
    health.clearDirty()

    health.max = 120

    assertTrue(health.isDirty())
    assertEquals(120, health.max)
  }

  @Test
  fun `multiple changes keep dirty flag true until cleared`() {
    val health = Health(50, 100)
    health.clearDirty()

    health.current = 60
    health.max = 120

    assertTrue(health.isDirty())

    health.clearDirty()
    assertFalse(health.isDirty())
  }

  @Test
  fun `setting current higher than max clamps to max and sets dirty flag only if final value differs`() {
    val health = Health(50, 100)
    health.clearDirty()

    health.current = 150

    assertTrue(health.isDirty())
    assertEquals(100, health.current)
  }

  @Test
  fun `setting current to value that gets clamped to same value does not set dirty flag`() {
    val health = Health(100, 100)
    health.clearDirty()

    // Try to set to 150, but it will be clamped to 100 (same as current value)
    health.current = 150

    // Should not be dirty because final value (100) is same as before
    assertFalse(health.isDirty())
    assertEquals(100, health.current)
  }

  @Test
  fun `reducing max below current adjusts current and sets dirty flag`() {
    val health = Health(80, 100)
    health.clearDirty()

    health.max = 60

    assertTrue(health.isDirty())
    assertEquals(60, health.max)
    assertEquals(60, health.current)
  }

  @Test
  fun `setting current to negative value clamps to zero and sets dirty flag if current was not zero`() {
    val health = Health(50, 100)
    health.clearDirty()

    health.current = -10

    assertTrue(health.isDirty())
    assertEquals(0, health.current)
  }

  @Test
  fun `setting current to negative value when already zero does not set dirty flag`() {
    val health = Health(0, 100)
    health.clearDirty()

    health.current = -10

    assertFalse(health.isDirty())
    assertEquals(0, health.current)
  }

  @Test
  fun `toEntityMessage creates correct HealthComponentSMSG`() {
    val health = Health(75, 150)
    val entityId = 12345L

    val message = health.toEntityMessage(entityId)

    assertTrue(message is HealthComponentSMSG)
    val healthMessage = message as HealthComponentSMSG
    assertEquals(entityId, healthMessage.entityId)
    assertEquals(75, healthMessage.current)
    assertEquals(150, healthMessage.max)
  }

  @Test
  fun `dirty flag behavior when setting current to zero from non-zero`() {
    val health = Health(50, 100)
    health.clearDirty()

    health.current = 0

    assertTrue(health.isDirty())
    assertEquals(0, health.current)
  }

  @Test
  fun `setting max to current value adjusts current and sets dirty flag`() {
    val health = Health(50, 100)
    health.clearDirty()

    health.max = 50

    assertTrue(health.isDirty())
    assertEquals(50, health.max)
    assertEquals(50, health.current)
  }

  @Test
  fun `constructor with zero values works correctly`() {
    val health = Health(0, 0)

    assertEquals(0, health.current)
    assertEquals(0, health.max)
    assertTrue(health.isDirty())
  }

  @Test
  fun `constructor with equal current and max values works correctly`() {
    val health = Health(100, 100)

    assertEquals(100, health.current)
    assertEquals(100, health.max)
    assertTrue(health.isDirty())
  }

  @Test
  fun `constructor with current higher than max clamps current to max`() {
    val health = Health(150, 100)

    assertEquals(100, health.current)
    assertEquals(100, health.max)
    assertTrue(health.isDirty())
  }

  @Test
  fun `constructor with negative current clamps to zero`() {
    val health = Health(-10, 100)

    assertEquals(0, health.current)
    assertEquals(100, health.max)
    assertTrue(health.isDirty())
  }
}
