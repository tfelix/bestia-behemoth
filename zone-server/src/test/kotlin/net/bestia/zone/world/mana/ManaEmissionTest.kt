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

  private fun motesAt(): Double = field.deltaAt(AT.x.toDouble(), AT.y.toDouble()) / ManaField.DELTA_PER_MOTE

  private companion object {
    val AT = Vec3L(500, 500, 0)
  }
}
