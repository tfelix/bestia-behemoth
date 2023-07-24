package net.bestia.zoneserver.battle

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class BattleUtilTest {

  @Test
  fun `Float clamp contains the value in between`() {
    var r = 0.5f.clamp(0f, 1.0f)
    Assertions.assertEquals(0.5f, r, 0.01f)

    r = (-0.5f).clamp(0f, 1.0f)
    Assertions.assertEquals(0f, r, 0.01f)

    r = (1.5f).clamp(0f, 1.0f)
    Assertions.assertEquals(1f, r, 0.01f)
  }

  @Test
  fun `Double clamp contains the value in between`() {
    var r = 0.5.clamp(0.0, 1.0)
    Assertions.assertEquals(0.5, r, 0.01)

    r = (-0.5).clamp(0.0, 1.0)
    Assertions.assertEquals(0.0, r, 0.01)

    r = (1.5).clamp(0.0, 1.0)
    Assertions.assertEquals(1.0, r, 0.01)
  }
}