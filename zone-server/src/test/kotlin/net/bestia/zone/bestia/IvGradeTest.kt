package net.bestia.zone.bestia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IvGradeTest {

  @Test
  fun `each grade starts at its documented value`() {
    assertEquals(IvGrade.POOR, IvGrade.of(0))
    assertEquals(IvGrade.POOR, IvGrade.of(19))
    assertEquals(IvGrade.FAIR, IvGrade.of(20))
    assertEquals(IvGrade.GOOD, IvGrade.of(59))
    assertEquals(IvGrade.GREAT, IvGrade.of(60))
    assertEquals(IvGrade.EXCELLENT, IvGrade.of(94))
    assertEquals(IvGrade.PERFECT, IvGrade.of(95))
    assertEquals(IvGrade.PERFECT, IvGrade.of(100))
  }
}
