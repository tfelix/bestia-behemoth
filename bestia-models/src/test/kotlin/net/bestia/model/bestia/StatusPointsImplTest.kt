package net.bestia.model.bestia

import org.junit.Assert
import org.junit.Test

class StatusPointsImplTest {

  @Test
  fun check_invalid_sp_armor() {
    val sp = BasicStatusValues()

    sp.setMagicDefense(-10)
    Assert.assertEquals(0, sp.magicDefense.toLong())
    sp.setMagicDefense(10)
    Assert.assertEquals(10, sp.magicDefense.toLong())
    sp.setMagicDefense(1100)
    Assert.assertEquals(1000, sp.magicDefense.toLong())
  }

  @Test
  fun check_invalid_armor() {
    val sp = BasicStatusValues()

    sp.setDefense(-10)
    Assert.assertEquals(0, sp.physicalDefense.toLong())
    sp.setDefense(10)
    Assert.assertEquals(10, sp.physicalDefense.toLong())
    sp.setDefense(1100)
    Assert.assertEquals(1000, sp.physicalDefense.toLong())
  }

  @Test
  fun check_add() {
    val sp1 = BasicStatusValues()

    sp1.setStrength(10)
    sp1.setVitality(10)
    sp1.setIntelligence(10)
    sp1.setWillpower(10)
    sp1.setAgility(10)
    sp1.setDexterity(10)

    sp1.setDefense(10)
    sp1.setMagicDefense(10)


    sp1.add(sp1)

    Assert.assertEquals(20, sp1.physicalDefense.toLong())
    Assert.assertEquals(20, sp1.magicDefense.toLong())

    Assert.assertEquals(20, sp1.intelligence.toLong())
    Assert.assertEquals(20, sp1.vitality.toLong())
    Assert.assertEquals(20, sp1.vitality.toLong())
  }

  @Test
  fun test_instanciation() {
    val (strength, vitality, intelligence, willpower, agility, _, physicalDefense, magicDefense) = BasicStatusValues()

    Assert.assertEquals(0, agility.toLong())
    Assert.assertEquals(0, vitality.toLong())
    Assert.assertEquals(0, intelligence.toLong())
    Assert.assertEquals(0, agility.toLong())
    Assert.assertEquals(0, willpower.toLong())
    Assert.assertEquals(0, strength.toLong())

    Assert.assertEquals(0, physicalDefense.toLong())
    Assert.assertEquals(0, magicDefense.toLong())
  }
}
