package net.bestia.model.bestia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BasicStatusTest {

  @Test
  fun check_add() {
    val sp1 = BasicStatusValues(
        strength = 10,
        vitality = 10,
        intelligence = 10,
        willpower = 10,
        agility = 10,
        dexterity = 10
    )

    val restult = sp1 + sp1

    assertEquals(20, restult.intelligence)
    assertEquals(20, restult.vitality)
    assertEquals(20, restult.vitality)
  }

  @Test
  fun test_creation() {
    val sut = BasicStatusValues()

    assertEquals(1, sut.agility)
    assertEquals(1, sut.vitality)
    assertEquals(1, sut.intelligence)
    assertEquals(1, sut.agility)
    assertEquals(1, sut.willpower)
    assertEquals(1, sut.strength)
  }
}
