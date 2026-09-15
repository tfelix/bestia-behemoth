package net.bestia.zone.ecs.battle.effects

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AreaEffectTest {

  /** A tick dealing nothing still broadcasts a damage tag, which would read as a "0" on the client. */
  @Test
  fun `an effect that deals no damage is refused`() {
    assertThrows<IllegalArgumentException> {
      AreaEffect.lasting(
        casterId = 1,
        skillId = 1,
        skillLevel = 1,
        radiusTiles = 1,
        damagePerTick = 0,
        tickIntervalSeconds = 1f,
        durationSeconds = 1f
      )
    }
  }
}
