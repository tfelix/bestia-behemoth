package net.bestia.zone.bestia

import net.bestia.bnet.proto.BestiaGeneticsProto.IvGrade.IV_GRADE_FAIR
import net.bestia.bnet.proto.BestiaGeneticsProto.IvGrade.IV_GRADE_PERFECT
import net.bestia.zone.battle.status.IndividualValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IvReadingTest {

  private val ivs = IndividualValues(strength = 97, intelligence = 25)

  @Test
  fun `a master without Bloodline Study reads nine grades and no numbers`() {
    val reading = IvReading(ivs, exact = false).toBnet()

    assertEquals(9, reading.gradesCount)
    assertEquals(IV_GRADE_PERFECT, reading.getGrades(0))
    assertEquals(IV_GRADE_FAIR, reading.getGrades(1))
    assertTrue(reading.exactList.isEmpty())
  }

  @Test
  fun `a master with Bloodline Study reads the numbers in stat order`() {
    val reading = IvReading(ivs, exact = true).toBnet()

    assertEquals(listOf(97, 25, 0, 0, 0, 0, 0, 0, 0), reading.exactList)
  }
}
