package net.bestia.zone.world.mana

import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManaEmissionTest {

  private val field = ManaField()
  private val config = ManaEmissionConfig()
  private val sut = ManaEmission(field, config)

  @Test
  fun `a free cast releases nothing`() {
    sut.onCast(AT, manaCost = 0)

    assertEquals(0, field.tileCount)
  }

  @Test
  fun `a boss releases ten times what its level alone would`() {
    sut.onKill(AT, level = 20, boss = false)
    val ordinary = motesAt()

    sut.onKill(AT, level = 20, boss = true)

    assertEquals(11 * ordinary, motesAt(), 1e-9)
  }

  @Test
  fun `a broken store lets go of what every item held`() {
    sut.onStoreBroken(AT, "mana_crystal", amount = 3)

    assertEquals(3 * config.storedMotes.getValue("mana_crystal"), motesAt(), 1e-9)
  }

  @Test
  fun `a broken store of items that hold no mana releases nothing`() {
    sut.onStoreBroken(AT, "iron_ore", amount = 5)

    assertEquals(0, field.tileCount)
  }

  private fun motesAt(): Double = field.deltaAt(AT.x.toDouble(), AT.y.toDouble()) / ManaField.DELTA_PER_MOTE

  private companion object {
    val AT = Vec3L(500, 500, 0)
  }
}
