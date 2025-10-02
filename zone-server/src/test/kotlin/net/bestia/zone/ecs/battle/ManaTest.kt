package net.bestia.zone.ecs.battle

import net.bestia.zone.component.ManaComponentSMSG
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ManaTest {

  @Test
  fun `constructor with values sets current and max correctly`() {
    val mana = Mana(50, 100)

    assertEquals(50, mana.current)
    assertEquals(100, mana.max)
  }

  @Test
  fun `isDirty returns true initially after construction`() {
    val mana = Mana(50, 100)

    assertTrue(mana.isDirty())
  }

  @Test
  fun `clearDirty sets dirty flag to false`() {
    val mana = Mana(50, 100)

    mana.clearDirty()

    assertFalse(mana.isDirty())
  }

  @Test
  fun `setting current to same value does not set dirty flag`() {
    val mana = Mana(50, 100)
    mana.clearDirty()

    mana.current = 50

    assertFalse(mana.isDirty())
  }

  @Test
  fun `setting current to different valid value sets dirty flag`() {
    val mana = Mana(50, 100)
    mana.clearDirty()

    mana.current = 60

    assertTrue(mana.isDirty())
    assertEquals(60, mana.current)
  }

  @Test
  fun `setting max to same value does not set dirty flag`() {
    val mana = Mana(50, 100)
    mana.clearDirty()

    mana.max = 100

    assertFalse(mana.isDirty())
  }

  @Test
  fun `setting max to different value sets dirty flag`() {
    val mana = Mana(50, 100)
    mana.clearDirty()

    mana.max = 120

    assertTrue(mana.isDirty())
    assertEquals(120, mana.max)
  }

  @Test
  fun `multiple changes keep dirty flag true until cleared`() {
    val mana = Mana(50, 100)
    mana.clearDirty()

    mana.current = 60
    mana.max = 120

    assertTrue(mana.isDirty())

    mana.clearDirty()
    assertFalse(mana.isDirty())
  }

  @Test
  fun `setting current higher than max clamps to max and sets dirty flag only if final value differs`() {
    val mana = Mana(50, 100)
    mana.clearDirty()

    mana.current = 150

    assertTrue(mana.isDirty())
    assertEquals(100, mana.current)
  }

  @Test
  fun `setting current to value that gets clamped to same value does not set dirty flag`() {
    val mana = Mana(100, 100)
    mana.clearDirty()

    // Try to set to 150, but it will be clamped to 100 (same as current value)
    mana.current = 150

    // Should not be dirty because final value (100) is same as before
    assertFalse(mana.isDirty())
    assertEquals(100, mana.current)
  }

  @Test
  fun `reducing max below current adjusts current and sets dirty flag`() {
    val mana = Mana(80, 100)
    mana.clearDirty()

    mana.max = 60

    assertTrue(mana.isDirty())
    assertEquals(60, mana.max)
    assertEquals(60, mana.current)
  }

  @Test
  fun `setting current to negative value clamps to zero and sets dirty flag if current was not zero`() {
    val mana = Mana(50, 100)
    mana.clearDirty()

    mana.current = -10

    assertTrue(mana.isDirty())
    assertEquals(0, mana.current)
  }

  @Test
  fun `setting current to negative value when already zero does not set dirty flag`() {
    val mana = Mana(0, 100)
    mana.clearDirty()

    mana.current = -10

    assertFalse(mana.isDirty())
    assertEquals(0, mana.current)
  }

  @Test
  fun `toEntityMessage creates correct ManaComponentSMSG`() {
    val mana = Mana(75, 150)
    val entityId = 12345L

    val message = mana.toEntityMessage(entityId)

    assertTrue(message is ManaComponentSMSG)
    val manaMessage = message as ManaComponentSMSG
    assertEquals(entityId, manaMessage.entityId)
    assertEquals(75, manaMessage.current)
    assertEquals(150, manaMessage.max)
  }

  @Test
  fun `dirty flag behavior when setting current to zero from non-zero`() {
    val mana = Mana(50, 100)
    mana.clearDirty()

    mana.current = 0

    assertTrue(mana.isDirty())
    assertEquals(0, mana.current)
  }

  @Test
  fun `setting max to current value adjusts current and sets dirty flag`() {
    val mana = Mana(50, 100)
    mana.clearDirty()

    mana.max = 50

    assertTrue(mana.isDirty())
    assertEquals(50, mana.max)
    assertEquals(50, mana.current)
  }

  @Test
  fun `constructor with zero values works correctly`() {
    val mana = Mana(0, 0)

    assertEquals(0, mana.current)
    assertEquals(0, mana.max)
    assertTrue(mana.isDirty())
  }

  @Test
  fun `constructor with equal current and max values works correctly`() {
    val mana = Mana(100, 100)

    assertEquals(100, mana.current)
    assertEquals(100, mana.max)
    assertTrue(mana.isDirty())
  }

  @Test
  fun `constructor with current higher than max clamps current to max`() {
    val mana = Mana(150, 100)

    assertEquals(100, mana.current)
    assertEquals(100, mana.max)
    assertTrue(mana.isDirty())
  }

  @Test
  fun `constructor with negative current clamps to zero`() {
    val mana = Mana(-10, 100)

    assertEquals(0, mana.current)
    assertEquals(100, mana.max)
    assertTrue(mana.isDirty())
  }
}
