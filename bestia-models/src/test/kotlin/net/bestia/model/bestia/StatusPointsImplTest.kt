package net.bestia.model.bestia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StatusPointsImplTest {

  @Test
  fun check_add() {
    val sp1 = BasicStatusValues(
        strength = 10,
        vitality = 10,
        intelligence = 10,
        willpower = 10,
        agility = 10,
        dexterity = 10,
        magicDefense = 10,
        physicalDefense = 10
    )

    val restult = sp1 + sp1

    assertEquals(20, restult.physicalDefense)
    assertEquals(20, restult.magicDefense)

    assertEquals(20, restult.intelligence)
    assertEquals(20, restult.vitality)
    assertEquals(20, restult.vitality)
  }

  @Test
  fun test_instanciation() {
    val sut = BasicStatusValues()

    assertEquals(0, sut.agility)
    assertEquals(0, sut.vitality)
    assertEquals(0, sut.intelligence)
    assertEquals(0, sut.agility)
    assertEquals(0, sut.willpower)
    assertEquals(0, sut.strength)

    assertEquals(0, sut.physicalDefense)
    assertEquals(0, sut.magicDefense)
  }
}
