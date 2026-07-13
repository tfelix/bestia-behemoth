package net.bestia.zone.battle.buff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuffDefinitionTest {

  @Test
  fun `durationSeconds scales linearly with level`() {
    val def = BuffDefinition(
      id = 1L,
      identifier = "BLESSING",
      polarity = BuffPolarity.BUFF,
      showIcon = true,
      baseDurationSeconds = 60.0,
      durationPerLevel = 20.0
    )

    assertEquals(60.0, def.durationSeconds(1))
    assertEquals(240.0, def.durationSeconds(10))
  }

  @Test
  fun `durationSeconds with no per-level scaling stays constant`() {
    val def = BuffDefinition(
      id = 2L,
      identifier = "CONST",
      polarity = BuffPolarity.DEBUFF,
      showIcon = true,
      baseDurationSeconds = 15.0
    )

    assertEquals(15.0, def.durationSeconds(1))
    assertEquals(15.0, def.durationSeconds(5))
  }
}
