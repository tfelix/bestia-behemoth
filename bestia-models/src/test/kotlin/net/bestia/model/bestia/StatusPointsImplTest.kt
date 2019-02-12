package net.bestia.model.bestia

import org.junit.Assert
import org.junit.Test

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

    Assert.assertEquals(20, restult.physicalDefense)
    Assert.assertEquals(20, restult.magicDefense)

    Assert.assertEquals(20, restult.intelligence)
    Assert.assertEquals(20, restult.vitality)
    Assert.assertEquals(20, restult.vitality)
  }

  @Test
  fun test_instanciation() {
    val sut = BasicStatusValues()

    Assert.assertEquals(0, sut.agility)
    Assert.assertEquals(0, sut.vitality)
    Assert.assertEquals(0, sut.intelligence)
    Assert.assertEquals(0, sut.agility)
    Assert.assertEquals(0, sut.willpower)
    Assert.assertEquals(0, sut.strength)

    Assert.assertEquals(0, sut.physicalDefense)
    Assert.assertEquals(0, sut.magicDefense)
  }
}
