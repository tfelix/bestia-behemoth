package net.bestia.zone.battle.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StatusResistanceTest {

  @Test
  fun `the half point of attributes resists half`() {
    assertEquals(0.5, StatusResistance.of(statSum = 150, levelLead = 0), 1e-9)
  }

  @Test
  fun `every level the target leads by adds a point`() {
    assertEquals(0.1, StatusResistance.of(statSum = 0, levelLead = 10), 1e-9)
  }

  @Test
  fun `no amount of attributes makes immune`() {
    assertEquals(StatusResistance.MAX, StatusResistance.of(statSum = 10_000, levelLead = 50), 1e-9)
  }

  @Test
  fun `a stronger source cannot push resistance below zero`() {
    assertEquals(0.0, StatusResistance.of(statSum = 0, levelLead = -30), 1e-9)
  }

  @Test
  fun `resistance cuts the chance fully and the duration by half`() {
    assertEquals(30.0, StatusResistance.chancePercent(60.0, resistance = 0.5), 1e-9)
    assertEquals(7.5, StatusResistance.durationSeconds(10.0, resistance = 0.5), 1e-9)
  }
}
