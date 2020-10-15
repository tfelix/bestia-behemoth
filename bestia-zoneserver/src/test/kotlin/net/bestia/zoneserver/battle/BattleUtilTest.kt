package net.bestia.zoneserver.battle

import org.junit.Assert
import org.junit.Test

class BattleUtilTest {

  @Test
  fun `Float clamp contains the value in between`() {
    var r = 0.5f.clamp(0f, 1.0f)
    Assert.assertEquals(0.5f, r, 0.01f)

    r = (-0.5f).clamp(0f, 1.0f)
    Assert.assertEquals(0f, r, 0.01f)

    r = (1.5f).clamp(0f, 1.0f)
    Assert.assertEquals(1f, r, 0.01f)
  }

  @Test
  fun `Double clamp contains the value in between`() {
    var r = 0.5.clamp(0.0, 1.0)
    Assert.assertEquals(0.5, r, 0.01)

    r = (-0.5).clamp(0.0, 1.0)
    Assert.assertEquals(0.0, r, 0.01)

    r = (1.5).clamp(0.0, 1.0)
    Assert.assertEquals(1.0, r, 0.01)
  }
}