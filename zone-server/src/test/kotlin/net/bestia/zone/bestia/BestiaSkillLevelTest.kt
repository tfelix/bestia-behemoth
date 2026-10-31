package net.bestia.zone.bestia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BestiaSkillLevelTest {

  @Test
  fun `a skill learned later is known at a higher level, up to ten`() {
    assertEquals(1, BestiaSkillLevel.forLearnLevel(1))
    assertEquals(1, BestiaSkillLevel.forLearnLevel(6))
    assertEquals(2, BestiaSkillLevel.forLearnLevel(11))
    assertEquals(8, BestiaSkillLevel.forLearnLevel(70))
    assertEquals(10, BestiaSkillLevel.forLearnLevel(94))
    assertEquals(10, BestiaSkillLevel.forLearnLevel(100))
  }
}
